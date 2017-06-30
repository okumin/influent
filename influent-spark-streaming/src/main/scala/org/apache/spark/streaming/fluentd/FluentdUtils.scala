package org.apache.spark.streaming.fluentd

import org.apache.spark.storage.StorageLevel
import org.apache.spark.streaming.StreamingContext

object FluentdUtils {
  def createStream(ssc: StreamingContext,
                   host: String,
                   port: Int,
                   storageLevel: StorageLevel): FluentdInputDStream = {
    new FluentdInputDStream(ssc, host, port, storageLevel)
  }
}
