package net.feedbacky.app.service.comment;

import net.feedbacky.app.config.UserAuthenticationToken;
import net.feedbacky.app.controller.about.EmojiDataRegistry;
import net.feedbacky.app.data.board.moderator.Moderator;
import net.feedbacky.app.data.board.webhook.Webhook;
import net.feedbacky.app.data.board.webhook.WebhookDataBuilder;
import net.feedbacky.app.data.board.webhook.WebhookExecutor;
import net.feedbacky.app.data.idea.Idea;
import net.feedbacky.app.data.idea.comment.Comment;
import net.feedbacky.app.data.idea.comment.reaction.CommentReaction;
import net.feedbacky.app.data.idea.dto.comment.FetchCommentDto;
import net.feedbacky.app.data.idea.dto.comment.PatchCommentDto;
import net.feedbacky.app.data.idea.dto.comment.PostCommentDto;
import net.feedbacky.app.data.idea.dto.comment.reaction.FetchCommentReactionDto;
import net.feedbacky.app.data.idea.dto.comment.reaction.PostCommentReactionDto;
import net.feedbacky.app.data.idea.subscribe.NotificationEvent;
import net.feedbacky.app.data.idea.subscribe.SubscriptionExecutor;
import net.feedbacky.app.data.user.User;
import net.feedbacky.app.exception.FeedbackyRestException;
import net.feedbacky.app.exception.types.InsufficientPermissionsException;
import net.feedbacky.app.exception.types.InvalidAuthenticationException;
import net.feedbacky.app.exception.types.ResourceNotFoundException;
import net.feedbacky.app.repository.UserRepository;
import net.feedbacky.app.repository.idea.CommentRepository;
import net.feedbacky.app.repository.idea.IdeaRepository;
import net.feedbacky.app.service.ServiceUser;
import net.feedbacky.app.util.PaginableRequest;
import net.feedbacky.app.util.SortFilterResolver;
import net.feedbacky.app.util.request.InternalRequestValidator;
import net.feedbacky.app.util.request.ServiceValidator;

import com.cosium.spring.data.jpa.entity.graph.domain.EntityGraphs;

import org.apache.commons.text.StringEscapeUtils;
import org.modelmapper.Conditions;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.text.MessageFormat;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @author Plajer
 * <p>
 * Created at 14.10.2019
 */
@Service
public class CommentServiceImpl implements CommentService {

  private final CommentRepository commentRepository;
  private final IdeaRepository ideaRepository;
  private final UserRepository userRepository;
  private final SubscriptionExecutor subscriptionExecutor;
  private final WebhookExecutor webhookExecutor;
  private final EmojiDataRegistry emojiDataRegistry;

  @Autowired
  public CommentServiceImpl(CommentRepository commentRepository, IdeaRepository ideaRepository, UserRepository userRepository, SubscriptionExecutor subscriptionExecutor,
                            WebhookExecutor webhookExecutor, EmojiDataRegistry emojiDataRegistry) {
    this.commentRepository = commentRepository;
    this.ideaRepository = ideaRepository;
    this.userRepository = userRepository;
    this.subscriptionExecutor = subscriptionExecutor;
    this.webhookExecutor = webhookExecutor;
    this.emojiDataRegistry = emojiDataRegistry;
  }

  @Override
  public PaginableRequest<List<FetchCommentDto>> getAllForIdea(long ideaId, int page, int pageSize, SortType sortType) {
    User user = null;
    if(SecurityContextHolder.getContext().getAuthentication() instanceof UserAuthenticationToken) {
      UserAuthenticationToken auth = (UserAuthenticationToken) SecurityContextHolder.getContext().getAuthentication();
      user = userRepository.findByEmail(((ServiceUser) auth.getPrincipal()).getEmail()).orElse(null);
    }
    Idea idea = ideaRepository.findById(ideaId)
            .orElseThrow(() -> new ResourceNotFoundException(MessageFormat.format("Idea with id {0} not found.", ideaId)));
    Page<Comment> pageData = commentRepository.findByIdea(idea, PageRequest.of(page, pageSize, SortFilterResolver.resolveCommentsSorting(sortType)));
    List<Comment> comments = pageData.getContent();
    int totalPages = pageData.getTotalElements() == 0 ? 0 : pageData.getTotalPages() - 1;
    final User finalUser = user;
    boolean isModerator = idea.getBoard().getModerators().stream().anyMatch(mod -> mod.getUser().equals(finalUser));
    List<FetchCommentDto> returnData = comments.stream().map(c -> {
      FetchCommentDto dto = new FetchCommentDto().from(c);
      if(!isModerator && c.getViewType() == Comment.ViewType.INTERNAL) {
        dto = dto.asInternalInvisible();
      }
      return dto;
    }).collect(Collectors.toList());
    return new PaginableRequest<>(new PaginableRequest.PageMetadata(page, totalPages, pageSize), returnData);
  }

  @Override
  public FetchCommentDto getOne(long id) {
    User user = null;
    if(SecurityContextHolder.getContext().getAuthentication() instanceof UserAuthenticationToken) {
      UserAuthenticationToken auth = (UserAuthenticationToken) SecurityContextHolder.getContext().getAuthentication();
      user = userRepository.findByEmail(((ServiceUser) auth.getPrincipal()).getEmail()).orElse(null);
    }
    Comment comment = commentRepository.findById(id, EntityGraphs.named("Comments.fetch"))
            .orElseThrow(() -> new ResourceNotFoundException(MessageFormat.format("Comment with id {0} not found.", id)));
    final User finalUser = user;
    boolean isModerator = comment.getIdea().getBoard().getModerators().stream().anyMatch(mod -> mod.getUser().equals(finalUser));
    if(comment.getViewType() == Comment.ViewType.INTERNAL && !isModerator) {
      throw new FeedbackyRestException(HttpStatus.BAD_REQUEST, "No permission to view this comment.");
    }
    return new FetchCommentDto().from(comment);
  }

  @Override
  public ResponseEntity<FetchCommentDto> post(PostCommentDto dto) {
    UserAuthenticationToken auth = InternalRequestValidator.getContextAuthentication();
    User user = userRepository.findByEmail(((ServiceUser) auth.getPrincipal()).getEmail())
            .orElseThrow(() -> new InvalidAuthenticationException("Session not found. Try again with new token."));
    Idea idea = ideaRepository.findById(dto.getIdeaId())
            .orElseThrow(() -> new ResourceNotFoundException(MessageFormat.format("Idea with id {0} not found.", dto.getIdeaId())));
    boolean isModerator = idea.getBoard().getModerators().stream().anyMatch(mod -> mod.getUser().equals(user));
    //1. internal type is for moderators only
    //2. restricted commenting is for moderators only
    if(!isModerator && (Comment.ViewType.valueOf(dto.getType().toUpperCase()) == Comment.ViewType.INTERNAL || idea.isCommentingRestricted())) {
      throw new InsufficientPermissionsException();
    }
    if(idea.getStatus() == Idea.IdeaStatus.CLOSED && !idea.getBoard().isClosedIdeasCommentingEnabled()) {
      throw new FeedbackyRestException(HttpStatus.BAD_REQUEST, "Idea already closed.");
    }
    Comment comment = new Comment();
    comment.setId(null);
    comment.setIdea(idea);
    comment.setCreator(user);
    comment.setReactions(new HashSet<>());
    comment.setSpecial(false);
    comment.setSpecialType(Comment.SpecialType.LEGACY);
    comment.setViewType(Comment.ViewType.valueOf(dto.getType().toUpperCase()));
    comment.setDescription(StringEscapeUtils.escapeHtml4(dto.getDescription()));
    if(dto.getReplyTo() != null) {
      Comment repliedComment = commentRepository.findById(dto.getReplyTo())
              .orElseThrow(() -> new ResourceNotFoundException(MessageFormat.format("Reply comment with id {0} not found.", dto.getReplyTo())));
      comment.setReplyTo(repliedComment);
      subscriptionExecutor.notifySubscriber(idea, user, new NotificationEvent(SubscriptionExecutor.Event.COMMENT_REPLY, user,
              comment, StringEscapeUtils.unescapeHtml4(comment.getDescription())));
    }

    if(commentRepository.findByCreatorAndDescriptionAndIdea(user, comment.getDescription(), idea).isPresent()) {
      throw new FeedbackyRestException(HttpStatus.BAD_REQUEST, "Can't post duplicated comments.");
    }
    commentRepository.save(comment);
    //to force trend score update
    Set<Comment> comments = idea.getComments();
    comments.add(comment);
    idea.setComments(comments);
    ideaRepository.save(idea);

    //do not publish information about private internal comments
    if(comment.getViewType() != Comment.ViewType.INTERNAL) {
      WebhookDataBuilder webhookBuilder = new WebhookDataBuilder().withUser(user).withIdea(idea).withComment(comment);
      webhookExecutor.executeWebhooks(idea.getBoard(), Webhook.Event.IDEA_COMMENT, webhookBuilder.build());

      //notify only if moderator
      if(isModerator) {
        subscriptionExecutor.notifySubscribers(idea, new NotificationEvent(SubscriptionExecutor.Event.IDEA_BY_MODERATOR_COMMENT, user,
                comment, StringEscapeUtils.unescapeHtml4(comment.getDescription())));
      }
    }
    return ResponseEntity.status(HttpStatus.CREATED).body(new FetchCommentDto().from(comment));
  }

  @Override
  public FetchCommentReactionDto postReaction(long id, PostCommentReactionDto dto) {
    UserAuthenticationToken auth = InternalRequestValidator.getContextAuthentication();
    User user = userRepository.findByEmail(((ServiceUser) auth.getPrincipal()).getEmail())
            .orElseThrow(() -> new InvalidAuthenticationException("Session not found. Try again with new token."));
    Comment comment = commentRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException(MessageFormat.format("Comment with id {0} not found.", id)));
    if(comment.isSpecial()) {
      throw new FeedbackyRestException(HttpStatus.BAD_REQUEST, "Can't react to this comment.");
    }
    if(emojiDataRegistry.getEmojis().stream().noneMatch(e -> e.getId().equals(dto.getReactionId()))) {
      throw new FeedbackyRestException(HttpStatus.BAD_REQUEST, "Invalid reaction.");
    }
    if(comment.getReactions().stream().anyMatch(r -> r.getUser().equals(user) && r.getReactionId().equals(dto.getReactionId()))) {
      throw new FeedbackyRestException(HttpStatus.BAD_REQUEST, "Already reacted.");
    }
    CommentReaction reaction = new CommentReaction();
    reaction.setComment(comment);
    reaction.setReactionId(dto.getReactionId());
    reaction.setUser(user);
    comment.getReactions().add(reaction);
    commentRepository.save(comment);
    reaction = comment.getReactions().stream().filter(r -> r.getReactionId().equals(dto.getReactionId()) && r.getUser().equals(user)).findFirst().get();
    return new FetchCommentReactionDto().from(reaction);
  }

  @Override
  public FetchCommentDto patch(long id, PatchCommentDto dto) {
    UserAuthenticationToken auth = InternalRequestValidator.getContextAuthentication();
    User user = userRepository.findByEmail(((ServiceUser) auth.getPrincipal()).getEmail())
            .orElseThrow(() -> new InvalidAuthenticationException("Session not found. Try again with new token."));
    Comment comment = commentRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException(MessageFormat.format("Comment with id {0} not found.", id)));
    if(!comment.getCreator().getId().equals(user.getId())) {
      throw new InsufficientPermissionsException();
    }

    long creationTimeDiffMillis = Math.abs(Calendar.getInstance().getTime().getTime() - comment.getCreationDate().getTime());
    long minutesDiff = TimeUnit.MINUTES.convert(creationTimeDiffMillis, TimeUnit.MILLISECONDS);
    //mark comments edited only if they were posted later than 5 minutes for any typo fixes etc.
    if(dto.getDescription() != null
            && !comment.getDescription().equals(StringEscapeUtils.escapeHtml4(StringEscapeUtils.unescapeHtml4(comment.getDescription())))
            && minutesDiff > 5) {
      comment.setEdited(true);
    }
    ModelMapper mapper = new ModelMapper();
    mapper.getConfiguration().setPropertyCondition(Conditions.isNotNull());
    mapper.map(dto, comment);

    comment.setDescription(StringEscapeUtils.escapeHtml4(StringEscapeUtils.unescapeHtml4(comment.getDescription())));
    commentRepository.save(comment);
    return new FetchCommentDto().from(comment);
  }

  @Override
  public ResponseEntity delete(long id) {
    UserAuthenticationToken auth = InternalRequestValidator.getContextAuthentication();
    User user = userRepository.findByEmail(((ServiceUser) auth.getPrincipal()).getEmail())
            .orElseThrow(() -> new InvalidAuthenticationException("Session not found. Try again with new token."));
    Comment comment = commentRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException(MessageFormat.format("Comment with id {0} not found.", id)));
    if(!comment.getCreator().equals(user) && !ServiceValidator.hasPermission(comment.getIdea().getBoard(), Moderator.Role.MODERATOR, user)) {
      throw new InsufficientPermissionsException();
    }
    WebhookDataBuilder builder = new WebhookDataBuilder().withUser(user).withIdea(comment.getIdea()).withComment(comment);
    Idea idea = comment.getIdea();
    webhookExecutor.executeWebhooks(idea.getBoard(), Webhook.Event.IDEA_COMMENT_DELETE, builder.build());
    commentRepository.delete(comment);
    //to force trend score update
    Set<Comment> comments = idea.getComments();
    comments.remove(comment);
    idea.setComments(comments);
    ideaRepository.save(idea);
    return ResponseEntity.noContent().build();
  }

  @Override
  public ResponseEntity deleteReaction(long id, String reactionId) {
    UserAuthenticationToken auth = InternalRequestValidator.getContextAuthentication();
    User user = userRepository.findByEmail(((ServiceUser) auth.getPrincipal()).getEmail())
            .orElseThrow(() -> new InvalidAuthenticationException("Session not found. Try again with new token."));
    Comment comment = commentRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException(MessageFormat.format("Comment with id {0} not found.", id)));
    if(comment.isSpecial()) {
      throw new FeedbackyRestException(HttpStatus.BAD_REQUEST, "Can't react to this comment.");
    }
    if(emojiDataRegistry.getEmojis().stream().noneMatch(e -> e.getId().equals(reactionId))) {
      throw new FeedbackyRestException(HttpStatus.BAD_REQUEST, "Invalid reaction.");
    }
    Optional<CommentReaction> optional = comment.getReactions().stream().filter(r -> r.getUser().equals(user) && r.getReactionId().equals(reactionId)).findFirst();
    if(!optional.isPresent()) {
      throw new FeedbackyRestException(HttpStatus.BAD_REQUEST, "Not yet reacted.");
    }
    CommentReaction reaction = optional.get();
    comment.getReactions().remove(reaction);
    reaction.setComment(null);
    commentRepository.save(comment);
    //no need to expose
    return ResponseEntity.noContent().build();
  }
}
