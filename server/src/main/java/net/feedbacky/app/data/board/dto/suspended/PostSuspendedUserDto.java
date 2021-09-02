package net.feedbacky.app.data.board.dto.suspended;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import net.feedbacky.app.annotation.date.DateTimestamp;
import net.feedbacky.app.data.board.Board;
import net.feedbacky.app.data.board.suspended.SuspendedUser;
import net.feedbacky.app.data.user.User;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import javax.validation.constraints.NotNull;

import java.sql.Timestamp;

/**
 * @author Plajer
 * <p>
 * Created at 17.11.2020
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@NotNull
public class PostSuspendedUserDto {

  @NotNull(message = "User id cannot be empty.")
  private long userId;
  @NotNull(message = "Role cannot be empty.")
  @DateTimestamp
  private String suspensionEndDate;

  public SuspendedUser convertToEntity(User user, Board board) {
    SuspendedUser suspendedUser = new SuspendedUser();
    suspendedUser.setSuspensionEndDate(Timestamp.valueOf(suspensionEndDate));
    suspendedUser.setBoard(board);
    suspendedUser.setUser(user);
    return suspendedUser;
  }

}
