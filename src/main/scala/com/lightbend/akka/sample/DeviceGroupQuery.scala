package com.lightbend.akka.sample

import akka.actor.Actor.Receive
import akka.actor.{Actor, ActorLogging, ActorRef, Props, Terminated}
import com.lightbend.akka.sample.DeviceGroup.TemperatureReading
import com.lightbend.akka.sample.DeviceGroupQuery.ConnectionTimeout

import scala.concurrent.duration.FiniteDuration

/**
  * Created by perov on 7/15/2017.
  */
object DeviceGroupQuery {
  case object ConnectionTimeout

  def props(
           actorToDeviceId: Map[ActorRef, String],
           requestId: Long,
           requester: ActorRef,
           timeout: FiniteDuration
           ): Props = Props(new DeviceGroupQuery(actorToDeviceId, requestId, requester, timeout))
}

class DeviceGroupQuery(
                        actorToDeviceId: Map[ActorRef, String],
                        requestId: Long,
                        requester: ActorRef,
                        timeout: FiniteDuration
                      ) extends Actor with ActorLogging {

  import context.dispatcher

  val queryTimeoutTimer = context.system.scheduler.scheduleOnce(timeout, self, ConnectionTimeout)

  override def preStart(): Unit = {
    actorToDeviceId.keysIterator.foreach({ deviceActor =>
      context.watch(deviceActor)
      deviceActor ! Device.ReadTemperature(0)
    })
  }

  override def postStop(): Unit = queryTimeoutTimer.cancel()

  def receivedResponse(
                        deviceActor: ActorRef,
                        reading: TemperatureReading,
                        stillWaiting: Set[ActorRef],
                        repliesSoFar: Map[String, TemperatureReading]
                      ): Unit = {
    context.unwatch(deviceActor)
    val deviceId = actorToDeviceId(deviceActor)
    val newStillWaiting = stillWaiting - deviceActor

    val newRepliesSoFar = repliesSoFar + (deviceId -> reading)
    if (newStillWaiting.isEmpty) {
      requester ! DeviceGroup.RespondAllTemperatures(requestId, newRepliesSoFar)
      context.stop(self)
    } else {
      context.become(waitingForReplies(newRepliesSoFar, newStillWaiting))
    }
  }

  def waitingForReplies(repliesSoFar: Map[String, TemperatureReading], stillWaiting: Set[ActorRef]): Receive = {
    case Device.RespondTemperature(0, valueOption) =>
      val deviceActor = sender()
      val reading = valueOption match {
        case Some(value) => DeviceGroup.Temperature(value)
        case None => DeviceGroup.TemperatureNotAvailable
      }
      receivedResponse(deviceActor, reading, stillWaiting, repliesSoFar)
    case Terminated(deviceActor) =>
      receivedResponse(deviceActor, DeviceGroup.DeviceNotAvailable, stillWaiting, repliesSoFar)
    case ConnectionTimeout =>
      val timedOutReplies = stillWaiting.map({deviceActor =>
        val deviceId = actorToDeviceId(deviceActor)
        deviceId -> DeviceGroup.DeviceTimedOut
      })
      requester ! DeviceGroup.RespondAllTemperatures(requestId, repliesSoFar ++ timedOutReplies)
      context.stop(self)
  }

  override def receive: Receive =
    waitingForReplies(
      Map.empty,
      actorToDeviceId.keySet
    )
}
