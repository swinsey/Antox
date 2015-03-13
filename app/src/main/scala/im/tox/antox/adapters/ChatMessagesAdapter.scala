package im.tox.antox.adapters

import java.io.File
import java.sql.Timestamp
import java.util
import java.util.{Random, HashSet}

import android.app.AlertDialog
import android.content.{Context, DialogInterface, Intent}
import android.database.Cursor
import android.graphics.{Typeface, Color}
import android.net.Uri
import android.os.Environment
import android.support.v4.widget.ResourceCursorAdapter
import android.text.{ClipboardManager, Html}
import android.util.Log
import android.view.{Gravity, LayoutInflater, View, ViewGroup}
import android.view.animation.{Animation, AnimationUtils}
import android.widget._
import im.tox.antox.wrapper.ChatMessages
import im.tox.antox.R
import im.tox.antox.adapters.ChatMessagesAdapter._
import im.tox.antox.data.AntoxDB
import im.tox.antox.tox.ToxSingleton
import im.tox.antox.utils.{BitmapManager, Constants, TimestampUtils}
import rx.lang.scala.Observable
import rx.lang.scala.schedulers.IOScheduler


object ChatMessagesAdapter {

  class ChatMessagesHolder {

    var row: LinearLayout = _

    var layout: LinearLayout = _

    var background: LinearLayout = _

    var sentTriangle: View = _

    var receivedTriangle: View = _

    var message: TextView = _

    var time: TextView = _

    var imageMessage: ImageView = _

    var imageMessageFrame: FrameLayout = _

    var title: TextView = _

    var progress: ProgressBar = _

    var progressText: TextView = _

    var padding: View = _

    var buttons: LinearLayout = _

    var accept: View = _

    var reject: View = _

    var bubble: LinearLayout = _

    var wrapper: LinearLayout = _

  }
}

class ChatMessagesAdapter(var context: Context, c: Cursor, ids: util.HashSet[Integer])
  extends ResourceCursorAdapter(context, R.layout.chat_message_row, c, 0) {

  var layoutResourceId: Int = R.layout.chat_message_row

  private var density: Int = context.getResources.getDisplayMetrics.density.toInt

  private var anim: Animation = AnimationUtils.loadAnimation(this.context, R.anim.abc_slide_in_bottom)

  private var mInflater: LayoutInflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE).asInstanceOf[LayoutInflater]

  private var animatedIds: util.HashSet[Integer] = ids

  override def newView(context: Context, cursor: Cursor, parent: ViewGroup): View = {
    mInflater.inflate(this.layoutResourceId, parent, false)
  }

  override def bindView(view: View, context: Context, cursor: Cursor) {
    val msg = chatMessageFromCursor(cursor)
    var lastMsg: ChatMessages = null
    if (cursor.moveToPrevious()) {
      lastMsg = chatMessageFromCursor(cursor)
      cursor.moveToNext()
    }

    var nextMessage: ChatMessages = null
    if (cursor.moveToNext()) {
      nextMessage = chatMessageFromCursor(cursor)
      cursor.moveToPrevious()
    }

    val holder = new ChatMessagesHolder()
    holder.message = view.findViewById(R.id.message_text).asInstanceOf[TextView]
    holder.layout = view.findViewById(R.id.message_text_layout).asInstanceOf[LinearLayout]
    holder.row = view.findViewById(R.id.message_row_layout).asInstanceOf[LinearLayout]
    holder.background = view.findViewById(R.id.message_text_background).asInstanceOf[LinearLayout]
    holder.time = view.findViewById(R.id.message_text_date).asInstanceOf[TextView]
    holder.title = view.findViewById(R.id.message_title).asInstanceOf[TextView]
    holder.progress = view.findViewById(R.id.file_transfer_progress).asInstanceOf[ProgressBar]
    holder.imageMessage = view.findViewById(R.id.message_sent_photo).asInstanceOf[ImageView]
    holder.imageMessageFrame = view.findViewById(R.id.message_sent_photo_frame).asInstanceOf[FrameLayout]
    holder.progressText = view.findViewById(R.id.file_transfer_progress_text).asInstanceOf[TextView]
    holder.padding = view.findViewById(R.id.file_transfer_padding)
    holder.buttons = view.findViewById(R.id.file_buttons).asInstanceOf[LinearLayout]
    holder.accept = view.findViewById(R.id.file_accept_button)
    holder.reject = view.findViewById(R.id.file_reject_button)
    holder.sentTriangle = view.findViewById(R.id.sent_triangle)
    holder.receivedTriangle = view.findViewById(R.id.received_triangle)
    holder.bubble = view.findViewById(R.id.message_bubble).asInstanceOf[LinearLayout]
    holder.wrapper = view.findViewById(R.id.message_background_wrapper).asInstanceOf[LinearLayout]
    holder.message.setTextSize(16)
    holder.message.setVisibility(View.GONE)
    holder.time.setVisibility(View.GONE)
    holder.title.setVisibility(View.GONE)
    holder.progress.setVisibility(View.GONE)
    holder.imageMessage.setVisibility(View.GONE)
    holder.imageMessageFrame.setVisibility(View.GONE)
    holder.progressText.setVisibility(View.GONE)
    holder.padding.setVisibility(View.GONE)
    holder.buttons.setVisibility(View.GONE)
    holder.sentTriangle.setVisibility(View.GONE)
    holder.receivedTriangle.setVisibility(View.GONE)
    holder.bubble.setAlpha(1.0f)
    msg.`type` match {
      case Constants.MESSAGE_TYPE_OWN | Constants.MESSAGE_TYPE_GROUP_OWN =>
        holder.message.setText(msg.message)
        ownMessage(holder)
        holder.message.setVisibility(View.VISIBLE)
        if (!(msg.received)) {
          holder.bubble.setAlpha(0.5f)
        }

      case Constants.MESSAGE_TYPE_GROUP_PEER =>
        holder.message.setText(msg.message)
        holder.title.setText(msg.sender_name)
        groupMessage(holder, genNameColor(msg.sender_name))
        holder.message.setVisibility(View.VISIBLE)
        if (lastMsg == null || msg.sender_name != lastMsg.sender_name) {
          holder.title.setVisibility(View.VISIBLE)
        }
        if (!(msg.received)) {
          holder.bubble.setAlpha(0.5f)
        }

      case Constants.MESSAGE_TYPE_FRIEND =>
        holder.message.setText(msg.message)
        contactMessage(holder)
        holder.message.setVisibility(View.VISIBLE)

      case Constants.MESSAGE_TYPE_FILE_TRANSFER | Constants.MESSAGE_TYPE_FILE_TRANSFER_FRIEND =>
        if (msg.`type` == Constants.MESSAGE_TYPE_FILE_TRANSFER) {
          ownMessage(holder)
          val split = msg.message.split("/")
          holder.message.setText(split(split.length - 1))
          holder.message.setVisibility(View.VISIBLE)
        } else {
          contactMessage(holder)
          holder.message.setText(msg.message)
          holder.message.setVisibility(View.VISIBLE)
        }
        holder.title.setVisibility(View.VISIBLE)
        holder.title.setText(R.string.chat_file_transfer)
        if (msg.received) {
          holder.progressText.setText("Finished")
          holder.progressText.setVisibility(View.VISIBLE)
        } else {
          if (msg.sent) {
            if (msg.message_id != -1) {
              holder.progress.setVisibility(View.VISIBLE)
              holder.progress.setMax(msg.size)
              holder.progress.setProgress(ToxSingleton.getProgress(msg.id).toInt)
              val mProgress = ToxSingleton.getProgressSinceXAgo(msg.id, 500)
              val bytesPerSecond = mProgress match {
                case Some(p) => ((p._1 * 1000) / p._2).toInt
                case None => 0
              }
              if (bytesPerSecond != 0) {
                val secondsToComplete = msg.size / bytesPerSecond
                holder.progressText.setText(java.lang.Integer.toString(bytesPerSecond / 1024) + " KiB/s, " +
                  secondsToComplete +
                  " seconds left")
              } else {
                holder.progressText.setText(java.lang.Integer.toString(bytesPerSecond / 1024) + " KiB/s")
              }
              holder.progressText.setVisibility(View.VISIBLE)
            } else {
              holder.progressText.setText("Failed")
              holder.progressText.setVisibility(View.VISIBLE)
            }
          } else {
            if (msg.message_id != -1) {
              if (msg.isMine) {
                holder.progressText.setText("Sent filesending request")
              } else {
                holder.progressText.setText("")
                holder.buttons.setVisibility(View.VISIBLE)
                holder.accept.setOnClickListener(new View.OnClickListener() {

                  override def onClick(view: View) {
                    ToxSingleton.acceptFile(msg.key, msg.message_id, context)
                  }
                })
                holder.reject.setOnClickListener(new View.OnClickListener() {

                  override def onClick(view: View) {
                    ToxSingleton.rejectFile(msg.key, msg.message_id, context)
                  }
                })
              }
            } else {
              holder.progressText.setText("Rejected")
            }
            holder.progressText.setVisibility(View.VISIBLE)
          }
        }
        if (msg.received || msg.isMine) {
          var f: File = null
          if (msg.message.contains("/")) {
            f = new File(msg.message)
          } else {
            f = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
              Constants.DOWNLOAD_DIRECTORY)
            f = new File(f.getAbsolutePath + "/" + msg.message)
          }
          if (f.exists() && (msg.received || msg.isMine)) {
            val file = f
            val okFileExtensions = Array("jpg", "png", "gif", "jpeg")
            for (extension <- okFileExtensions) {
              //Log.d("ChatMessagesAdapter", file.getName.toLowerCase())
              //Log.d("ChatMessagesAdapter", extension)
              if (file.getName.toLowerCase.endsWith(extension)) {
                Log.d("ChatMessagesAdapter", "true")
                if (BitmapManager.checkValidImage(file)) {
                  BitmapManager.loadBitmap(file, file.getPath.hashCode, holder.imageMessage)
                  holder.imageMessage.setVisibility(View.VISIBLE)
                  holder.imageMessageFrame.setVisibility(View.VISIBLE)
                  holder.imageMessage.setOnClickListener(new View.OnClickListener() {

                    def onClick(v: View) {
                      val i = new Intent()
                      i.setAction(android.content.Intent.ACTION_VIEW)
                      i.setDataAndType(Uri.fromFile(file), "image/*")
                      ChatMessagesAdapter.this.context.startActivity(i)
                    }
                  })
                  if (msg.received) {
                    holder.message.setVisibility(View.GONE)
                    holder.title.setVisibility(View.GONE)
                    holder.progressText.setVisibility(View.GONE)
                  } else {
                    holder.padding.setVisibility(View.VISIBLE)
                  }
                }
                //break
              }
            }
          }
        }

      case Constants.MESSAGE_TYPE_ACTION =>
        actionMessage(holder)
        holder.message.setText(Html.fromHtml("<b>" + msg.message.replaceFirst(" ", "</b> "))) //make first word (username) bold

    }

    if (msg.`type` == Constants.MESSAGE_TYPE_GROUP_PEER) {
      val showTimestampInterval: Int = 61 * 1000 //in milliseconds
      //TODO: Only show a timestamp if the next message is more than a second after this one
      if (nextMessage == null ||
        msg.sender_name != nextMessage.sender_name) {

        holder.time.setText(TimestampUtils.prettyTimestamp(msg.time, isChat = true))
        holder.time.setVisibility(View.VISIBLE)
      } else {
        holder.time.setVisibility(View.GONE)
      }
    } else {
      holder.time.setText(TimestampUtils.prettyTimestamp(msg.time, isChat = true))
      holder.time.setVisibility(View.VISIBLE)
    }

    if (!animatedIds.contains(msg.id)) {
      holder.row.startAnimation(anim)
      animatedIds.add(msg.id)
    }
    holder.row.setOnLongClickListener(new View.OnLongClickListener() {

      override def onLongClick(view: View): Boolean = {
        if (msg.`type` == Constants.MESSAGE_TYPE_OWN || msg.`type` == Constants.MESSAGE_TYPE_FRIEND) {
          val builder = new AlertDialog.Builder(context)
          val items = Array[CharSequence](context.getResources.getString(R.string.message_copy), context.getResources.getString(R.string.message_delete))
          builder.setCancelable(true).setItems(items, new DialogInterface.OnClickListener() {

            def onClick(dialog: DialogInterface, index: Int) = index match {
              case 0 =>
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE).asInstanceOf[ClipboardManager]
                clipboard.setText(msg.message)

              case 1 =>
                Observable[Boolean](subscriber => {
                  val antoxDB = new AntoxDB(context.getApplicationContext)
                  antoxDB.deleteMessage(msg.id)
                  antoxDB.close()
                  ToxSingleton.updateMessages(context)
                  subscriber.onCompleted()
                }).subscribeOn(IOScheduler()).subscribe()

            }
          })
          val alert = builder.create()
          alert.show()
        } else {
          val builder = new AlertDialog.Builder(context)
          val items = Array[CharSequence](context.getResources.getString(R.string.message_delete))
          builder.setCancelable(true).setItems(items, new DialogInterface.OnClickListener() {

            def onClick(dialog: DialogInterface, index: Int) = index match {
              case 0 =>
                Observable[Boolean](subscriber => {
                  val antoxDB = new AntoxDB(context.getApplicationContext)
                  antoxDB.deleteMessage(msg.id)
                  antoxDB.close()
                  ToxSingleton.updateMessages(context)
                  subscriber.onCompleted()
                }).subscribeOn(IOScheduler()).subscribe()

            }
          })
          val alert = builder.create()
          alert.show()
        }
        true
      }
    })
  }

  override def getViewTypeCount: Int = 4

  override def newDropDownView(context: Context, cursor: Cursor, parent: ViewGroup): View = super.newDropDownView(context, cursor, parent)

  private def chatMessageFromCursor(cursor: Cursor): ChatMessages = {
    val id = cursor.getInt(0)
    val time = Timestamp.valueOf(cursor.getString(1))
    val message_id = cursor.getInt(2)
    val key = cursor.getString(3)
    val sender_name = cursor.getString(4)
    val message = cursor.getString(5)
    val received = cursor.getInt(6) > 0
    val read = cursor.getInt(7) > 0
    val sent = cursor.getInt(8) > 0
    val size = cursor.getInt(9)
    val messageType = cursor.getInt(10)

    new ChatMessages(id, message_id, key, sender_name, message, time, received, sent, size, messageType)
  }

  private def shouldGreentext(message: String): Boolean = {
    message.startsWith(">")
  }

  private def genNameColor(name: String): Int = {
    val goldenRatio = 0.618033988749895
    val hue: Double = (new Random(name.hashCode).nextFloat() + goldenRatio) % 1
    Color.HSVToColor(Array(hue.asInstanceOf[Float] * 360, 0.5f, 0.7f))
  }

  private def ownMessage(holder: ChatMessagesHolder) {
    holder.time.setGravity(Gravity.RIGHT)
    holder.sentTriangle.setVisibility(View.VISIBLE)
    holder.layout.setGravity(Gravity.RIGHT)
    if (shouldGreentext(holder.message.getText.toString)) {
      holder.message.setTextColor(context.getResources.getColor(R.color.green_light))
    } else {
      holder.message.setTextColor(context.getResources.getColor(R.color.white))
    }

    holder.row.setGravity(Gravity.RIGHT)
    holder.wrapper.setGravity(Gravity.RIGHT)
    holder.background.setBackgroundDrawable(context.getResources.getDrawable(R.drawable.conversation_item_sent_shape))
    holder.background.setPadding(8 * density, 8 * density, 8 * density, 8 * density)
  }

  private def groupMessage(holder: ChatMessagesHolder, nameColor: Int) {
    contactMessage(holder)
    holder.title.setTextColor(nameColor)
    holder.title.setTypeface(holder.title.getTypeface, Typeface.BOLD)
  }

  private def contactMessage(holder: ChatMessagesHolder) {
    if (shouldGreentext(holder.message.getText.toString)) {
      holder.message.setTextColor(context.getResources.getColor(R.color.green))
    } else {
      holder.message.setTextColor(context.getResources.getColor(R.color.black))
    }
    holder.receivedTriangle.setVisibility(View.VISIBLE)
    holder.time.setGravity(Gravity.LEFT)
    holder.layout.setGravity(Gravity.LEFT)
    holder.row.setGravity(Gravity.LEFT)
    holder.wrapper.setGravity(Gravity.LEFT)
    holder.background.setBackgroundDrawable(context.getResources.getDrawable(R.drawable.conversation_item_received_shape))
    holder.background.setPadding(8 * density, 8 * density, 8 * density, 8 * density)
  }

  private def actionMessage(holder: ChatMessagesHolder) {
    holder.message.setVisibility(View.VISIBLE)
    holder.message.setTextSize(18)
    holder.message.setTextColor(context.getResources.getColor(R.color.black))
    holder.time.setGravity(Gravity.CENTER)
    holder.layout.setGravity(Gravity.CENTER)
    holder.row.setGravity(Gravity.CENTER)
    holder.wrapper.setGravity(Gravity.CENTER)
    holder.background.setBackgroundColor(context.getResources.getColor(R.color.white_absolute))
    holder.background.setPadding(8 * density, 8 * density, 8 * density, 8 * density)
    holder.time.setVisibility(View.VISIBLE)
  }
}
