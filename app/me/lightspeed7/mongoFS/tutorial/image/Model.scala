package me.lightspeed7.mongoFS.tutorial.image

import org.bson.types.ObjectId
import com.mongodb.DBObject
import me.lightspeed7.mongofs.{ MongoFile, MongoFileConstants }
import me.lightspeed7.mongofs.url.MongoFileUrl
import play.api.libs.json.{ Format, JsError, JsString, JsSuccess, JsValue, Json }
import java.util.Date
import scala.util.Try

//
// Image Model in MongoDB
// //////////////////////////////
case class Image(
  _id: ObjectId,
  imageUrl: String,
  thumbUrl: Option[String],
  mediumUrl: Option[String],
  description: Option[String],
  tooltip: Option[String] //
  )

object Image {
  implicit val objectIdFormat: Format[ObjectId] = new Format[ObjectId] {

    def reads(json: JsValue) = {
      json match {
        case jsString: JsString => {
          if (ObjectId.isValid(jsString.value)) JsSuccess(new ObjectId(jsString.value))
          else JsError("Invalid ObjectId")
        }
        case other => JsError("Can't parse json path as an ObjectId. Json content = " + other.toString())
      }
    }

    def writes(oId: ObjectId): JsValue = {
      JsString(oId.toString)
    }
  }

  def fields() = {
    Image.getClass.getDeclaredFields.foldLeft(Map[String, Any]()) { (map, field) =>
      field.setAccessible(true)
      map + (field.getName -> field.get(this))
    }
  }

  implicit val imageFmt = Json.format[Image]

  implicit def fromMongoDB(obj: DBObject): Image = {
    val id: ObjectId = obj.get("_id").asInstanceOf[ObjectId]
    val imageUrl = obj.get("imageUrl").toString
    val mediumUrl = if (obj.get("mediumUrl") != null) Some(obj.get("mediumUrl").toString) else None
    val thumbUrl = if (obj.get("thumbUrl") != null) Some(obj.get("thumbUrl").toString) else None
    val description = if (obj.get("description") != null) Some(obj.get("description").toString) else None
    val tooltip = if (obj.get("tooltip") != null) Some(obj.get("tooltip").toString) else None

    Image(id, imageUrl, thumbUrl, mediumUrl, description, tooltip)
  }

}

//
// JSON class for Angular
// //////////////////////////////
case class UiImage(
  id: String,
  description: Option[String],
  tooltip: Option[String],
  fileName: String,
  size: Long,
  storage: Long,
  format: String,
  contentType: String //
  )

object UiImage {
  implicit val UiImageFmt = Json.format[UiImage]

  implicit def fromImage(obj: Image): Try[UiImage] = {
    for {
      url <- Try(MongoFileUrl.construct(obj.imageUrl))
      file <- ImageService.getMongoFile(url)
    } yield {
      UiImage(obj._id.toString(), obj.description, obj.tooltip, //
        file.getFilename, file.getLength, file.getStorageLength, //
        file.getString(MongoFileConstants.format), file.getContentType)
    }
  }
}

//
// Statistics data to front end
// //////////////////////////////
case class StatsData(files: Long, size: Long, storage: Long, serverTime: String = new Date().toString())
object StatsData {
  implicit val StatsDataFormat = Json.format[StatsData]
}

//
// Payload object sent to Angular
// //////////////////////////////
case class Payload(data: JsValue, target: String)
object Payload {
  implicit val payloadFormat = Json.format[Payload]
}
