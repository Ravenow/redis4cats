/*
 * Copyright 2018-2019 Gabriel Volpe
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.gvolpe.fs2redis

import cats.effect.IO
import com.github.gvolpe.fs2redis.connection._
import com.github.gvolpe.fs2redis.domain.DefaultChannel
import com.github.gvolpe.fs2redis.effect.Log
import com.github.gvolpe.fs2redis.interpreter.pubsub.Fs2PubSub
import fs2.{ Pipe, Stream }

import scala.concurrent.duration._
import scala.util.Random

object Fs2PubSubDemo extends LoggerIOApp {

  import Demo._

  private val eventsChannel = DefaultChannel("events")
  private val gamesChannel  = DefaultChannel("games")

  def sink(name: String): Pipe[IO, String, Unit] =
    _.evalMap(x => putStrLn(s"Subscriber: $name >> $x"))

  def stream(implicit log: Log[IO]): Stream[IO, Unit] =
    for {
      uri <- Stream.eval(Fs2RedisURI.make[IO](redisURI))
      client <- Stream.resource(Fs2RedisClient[IO](uri))
      pubSub <- Fs2PubSub.mkPubSubConnection[IO, String, String](client, stringCodec, uri)
      sub1 = pubSub.subscribe(eventsChannel)
      sub2 = pubSub.subscribe(gamesChannel)
      pub1 = pubSub.publish(eventsChannel)
      pub2 = pubSub.publish(gamesChannel)
      _ <- Stream(
            sub1.through(sink("#events")),
            sub2.through(sink("#games")),
            Stream.awakeEvery[IO](3.seconds) >> Stream.eval(IO(Random.nextInt(100).toString)).through(pub1),
            Stream.awakeEvery[IO](5.seconds) >> Stream.emit("Pac-Man!").through(pub2),
            Stream.awakeDelay[IO](11.seconds) >> pubSub.unsubscribe(gamesChannel),
            Stream.awakeEvery[IO](6.seconds) >> pubSub
              .pubSubSubscriptions(List(eventsChannel, gamesChannel))
              .evalMap(putStrLn)
          ).parJoin(6).drain
    } yield ()

  def program(implicit log: Log[IO]): IO[Unit] =
    stream.compile.drain

}
