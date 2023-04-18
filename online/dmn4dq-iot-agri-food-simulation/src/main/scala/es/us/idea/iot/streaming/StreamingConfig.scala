package es.us.idea.iot.streaming

import scala.beans.BeanProperty

class StreamingConfig {
  @BeanProperty var bootstrapServers: java.util.ArrayList[String] = new java.util.ArrayList()
  @BeanProperty var startingOffsets: String = ""
  @BeanProperty var topic: String = ""

  override def toString = s"StreamingConfig($bootstrapServers, $startingOffsets, $topic)"
}