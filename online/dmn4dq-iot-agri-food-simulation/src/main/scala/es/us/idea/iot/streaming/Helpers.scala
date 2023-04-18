package es.us.idea.iot.streaming

import org.apache.spark.sql.DataFrame
import org.apache.spark.sql.functions.expr

object Helpers {

  implicit class DataFrameHelpers(df: DataFrame) {

    def aggregateSensorData(sensor: String, listColumn: String = "l"): DataFrame =
      df.withColumn(sensor, expr(s"filter($listColumn, x -> x.sensor == '$sensor')"))
        .withColumn(sensor, expr(s"transform($sensor, x -> x.value)"))
        .withColumn(sensor, expr(
          s"aggregate($sensor, cast(0.0 as double), (value, buffer) -> buffer + value, buffer -> buffer / size($sensor) )"
        ))

  }

}
