package net.feedbacky.app.util;

import org.apache.commons.lang3.StringUtils;

/**
 * @author Plajer
 * <p>
 * Created at 20.11.2019
 */
public class EmojiFilter {

  private EmojiFilter() {
  }

  public static String replaceEmojisPreSanitized(String preSanitizedMessage) {
    preSanitizedMessage = StringUtils.replace(preSanitizedMessage, ":)", "\uD83D\uDE04");
    preSanitizedMessage = StringUtils.replace(preSanitizedMessage, ";)", "\uD83D\uDE09");
    preSanitizedMessage = StringUtils.replace(preSanitizedMessage, "<3", "\uD83D\uDE0D");
    preSanitizedMessage = StringUtils.replace(preSanitizedMessage, "8)", "\uD83D\uDE0E");
    preSanitizedMessage = StringUtils.replace(preSanitizedMessage, "x)", "\uD83D\uDE06");
    preSanitizedMessage = StringUtils.replace(preSanitizedMessage, ":P", "\uD83D\uDE1B");
    preSanitizedMessage = StringUtils.replace(preSanitizedMessage, ":O", "\uD83D\uDE2E");
    preSanitizedMessage = StringUtils.replace(preSanitizedMessage, ":(", "\uD83D\uDE26");
    preSanitizedMessage = StringUtils.replace(preSanitizedMessage, ":@", "\uD83D\uDE21");
    return preSanitizedMessage;
  }

}