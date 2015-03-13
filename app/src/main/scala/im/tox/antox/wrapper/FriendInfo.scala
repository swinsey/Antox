package im.tox.antox.wrapper

import java.sql.Timestamp
//remove if not needed

class FriendInfo(
  isOnline: Boolean,
  friendName: String,
  userStatus: String,
  statusMessage: String,
  friendKey: String,
  val lastMessage: String,
  val lastMessageTimestamp: Timestamp,
  val unreadCount: Int,
  alias: String) extends Friend(isOnline, friendName, userStatus, statusMessage, friendKey, alias) {

  /**
  Returns 'alias' if it has been set, otherwise returns 'name'.
   */
  def getAliasOrName(): String = {
    if (alias != "") alias else name
  }
}
