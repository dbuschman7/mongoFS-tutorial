package me.lightspeed7.mongoFS.tutorial.file

import play.api.libs.json.Json
import com.mongodb.DBObject
import org.bson.types.ObjectId
import me.lightspeed7.mongofs.MongoFile
import me.lightspeed7.mongofs.MongoFileConstants

case class UiFile(
  id: String,
  fileName: String,
  size: Long,
  storage: Long,
  format: String,
  contentType: String //
  )

object UiFile {
  implicit val UiImageFmt = Json.format[UiFile]

  implicit def fromMongoDB(file: MongoFile): UiFile = {

    UiFile(file.getId.toString, //
      file.getFilename, file.getLength, file.getStorageLength, //
      file.getString(MongoFileConstants.format), file.getContentType)
  }
}
