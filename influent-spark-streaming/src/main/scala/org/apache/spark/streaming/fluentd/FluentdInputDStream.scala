package org.apache.spark.streaming.fluentd

import influent.EventStream
import org.apache.spark.storage.StorageLevel
import org.apache.spark.streaming.StreamingContext
import org.apache.spark.streaming.dstream.ReceiverInputDStream
import org.apache.spark.streaming.receiver.Receiver

private[fluentd] class FluentdInputDStream(ssc: StreamingContext,
                                           host: String,
                                           port: Int,
                                           storageLevel: StorageLevel)
  extends ReceiverInputDStream[EventStream](ssc) {

  override def getReceiver(): Receiver[EventStream] = new FluentdReceiver(host, port, storageLevel)
}
