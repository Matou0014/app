package net.feedbacky.app.controller.about;

import net.feedbacky.app.FeedbackyApplication;
import net.feedbacky.app.data.AboutFeedbackyData;
import net.feedbacky.app.data.user.dto.FetchUserDto;
import net.feedbacky.app.login.LoginProvider;
import net.feedbacky.app.login.LoginProviderRegistry;
import net.feedbacky.app.repository.UserRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Plajer
 * <p>
 * Created at 11.03.2020
 */
@CrossOrigin
@RestController
public class AboutRestController {

  private final LoginProviderRegistry loginProviderRegistry;
  private final EmojiDataRegistry emojiDataRegistry;
  private final UserRepository userRepository;
  private AboutFeedbackyData aboutFeedbackyData = null;
  private boolean developmentMode = false;

  @Autowired
  public AboutRestController(ApplicationContext context, LoginProviderRegistry loginProviderRegistry, EmojiDataRegistry emojiDataRegistry, UserRepository userRepository) {
    this.loginProviderRegistry = loginProviderRegistry;
    this.emojiDataRegistry = emojiDataRegistry;
    this.userRepository = userRepository;
    this.developmentMode = (boolean) context.getBean("isDevelopmentMode");
  }

  @GetMapping("/v1/service/about")
  public ResponseEntity<AboutFeedbackyData> handle() {
    AboutFeedbackyData data;
    //lazy init to make sure all login providers are registered before
    if(this.aboutFeedbackyData == null) {
      boolean closedIdeasCommenting = Boolean.parseBoolean(System.getenv("SETTINGS_ALLOW_COMMENTING_CLOSED_IDEAS"));
      boolean mailLoginEnabled = Boolean.parseBoolean(System.getenv("SETTINGS_MAIL_LOGIN_ENABLED"));
      List<FetchUserDto> admins = userRepository.findByServiceStaffTrue().stream().map(user -> new FetchUserDto().from(user)).collect(Collectors.toList());
      List<LoginProvider.ProviderData> providers = loginProviderRegistry.getProviders().stream().filter(LoginProvider::isEnabled).map(LoginProvider::getProviderData).collect(Collectors.toList());
      data = new AboutFeedbackyData(FeedbackyApplication.BACKEND_VERSION, providers, admins, emojiDataRegistry.getEmojis(), closedIdeasCommenting, mailLoginEnabled, developmentMode);
      //only cache when there is at least 1 service admin registered (for first installation purposes)
      if(!admins.isEmpty()) {
        this.aboutFeedbackyData = data;
      }
    } else {
      data = this.aboutFeedbackyData;
    }
    return ResponseEntity.ok(data);
  }

}