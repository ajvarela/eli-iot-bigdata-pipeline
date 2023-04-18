package es.us.idea.iot.streaming

import com.mongodb.spark.config.WriteConfig
import org.apache.spark.sql.{DataFrame, Row, SparkSession}
import org.apache.spark.sql.functions.{avg, collect_list, date_format, expr, struct, sum, unix_timestamp, window}
import org.apache.spark.sql.types.{DataType, DataTypes}
import com.mongodb.spark.sql._
import es.us.idea.dmn4spark.dmn.executor.DMNExecutor
import es.us.idea.dmn4spark.dmn.loader.PathLoader
import org.apache.commons.io.IOUtils
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.Constructor
import scala.jdk.CollectionConverters.collectionAsScalaIterableConverter

import scala.io.Source
import scala.util.Try

object AggregationEvaluation extends App {

  val sourceYaml = Source.fromResource("streaming-config.yaml").mkString
  val yaml = new Yaml(new Constructor(classOf[StreamingConfig]))
  val streamingConfig = yaml.load(sourceYaml).asInstanceOf[StreamingConfig]

  val TOPIC = streamingConfig.topic
  val BOOTSTRAP_SERVERS = streamingConfig.bootstrapServers.asScala.mkString(", ")
  val STARTING_OFFSETS = streamingConfig.startingOffsets

  val spark = SparkSession.builder()
    .master("local")
    .appName("DMN4DQ Streaming - IoT scenario").getOrCreate()

  import spark.implicits._
  import Helpers._
  import es.us.idea.dmn4spark.spark.dsl.implicits._

  val streamingDF = spark.readStream
    .format("kafka")
    .option("kafka.bootstrap.servers", BOOTSTRAP_SERVERS)
    .option("subscribe", TOPIC)
    .option("startingOffsets", STARTING_OFFSETS)
    .load()

  val preparedDf = prepareData(streamingDF)

  val curatedDf = preparedDf.dmn.inputStream(
    IOUtils.toInputStream(Source.fromResource("models/iot_data_curation_streaming.dmn").mkString, "UTF-8")
  ).load().withOutputColumn("DMN").execute()

  val mongoOutput = curatedDf.writeStream
    .foreachBatch {(batchDF: DataFrame, batchId: Long) =>
      batchDF.write.mode("append").mongo(WriteConfig(Map("uri" -> "mongodb://estigia.lsi.us.es:12117", "database" -> "Streaming", "collection" -> "iot")))
    }
    .outputMode("update")
    .start()

  mongoOutput.awaitTermination()

  def prepareData(df: DataFrame): DataFrame =
      df.select($"value".cast(DataTypes.StringType))
      .map(formatRow)
      .toDF("Location", "sensor", "value", "timestamp")
      .withColumn("timestamp", $"timestamp".cast(DataTypes.TimestampType))
      .withWatermark("timestamp", "30 seconds") // timestamp must exist
      .groupBy($"Location", window($"timestamp", "30 seconds", "30 seconds"))
      .agg(collect_list(struct($"sensor", $"value")).as("l"))
      .aggregateSensorData("VW30cm")
      .aggregateSensorData("VW60cm")
      .aggregateSensorData("VW90cm")
      .aggregateSensorData("VW120cm")
      .aggregateSensorData("VW150cm")
      .aggregateSensorData("T30cm")
      .aggregateSensorData("T60cm")
      .aggregateSensorData("T90cm")
      .aggregateSensorData("T120cm")
      .aggregateSensorData("T150cm")
      .withColumn("Date", date_format($"window.start", "dd/MM/yyyy"))
      .withColumn("Time", date_format($"window.start", "HH:mm:ss"))

  def formatRow(row: Row): (Option[String], Option[String], Option[Double], Option[Long]) = {
    Try(row.getString(0).split(' ')).toEither.map(s => {
      (Try(s(0)).toOption, Try(s(1)).toOption, Try(s(2).toDouble).toOption, Try(s(3).toLong / 1000).toOption) // Spark TS is in seconds
    }) match {
      case Right(value) => value
      case Left(e) => (None, None, None, None)
    }
  }

}
