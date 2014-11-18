/**
 * Copyright 2014 Confluent Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.confluent.kafkarest.resources;

import io.confluent.kafkarest.Context;
import io.confluent.kafkarest.ProducerPool;
import io.confluent.kafkarest.entities.ProduceRequest;
import io.confluent.kafkarest.entities.ProduceResponse;
import io.confluent.kafkarest.entities.Topic;
import org.apache.kafka.clients.producer.ProducerRecord;

import javax.validation.Valid;
import javax.ws.rs.*;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.MediaType;
import java.util.List;
import java.util.Map;
import java.util.Vector;

@Path("/topics")
@Produces(MediaType.APPLICATION_JSON)
public class TopicsResource {
    private final Context ctx;

    public TopicsResource(Context ctx) {
        this.ctx = ctx;
    }

    @GET
    public List<Topic> list() {
        return ctx.getMetadataObserver().getTopics();
    }

    @GET
    @Path("/{topic}")
    public Topic getTopic(@PathParam("topic") String topicName) {
        Topic topic = ctx.getMetadataObserver().getTopic(topicName);
        if (topic == null)
            throw new NotFoundException();
        return topic;
    }

    @Path("/{topic}/partitions")
    public PartitionsResource getPartitions(@PathParam("topic") String topicName) {
        if (!ctx.getMetadataObserver().topicExists(topicName))
            throw new NotFoundException();
        return new PartitionsResource(ctx, topicName);
    }

    @POST
    @Path("/{topic}")
    public void produce(final @Suspended AsyncResponse asyncResponse, @PathParam("topic") String topicName, @Valid ProduceRequest request) {
        if (!ctx.getMetadataObserver().topicExists(topicName))
            throw new NotFoundException();

        try {
            List<ProducerRecord> kafkaRecords = new Vector<ProducerRecord>();
            for(ProduceRequest.ProduceRecord record : request.getRecords()) {
                kafkaRecords.add(new ProducerRecord(topicName, record.getKey(), record.getValue()));
            }
            ctx.getProducerPool().produce(kafkaRecords, new ProducerPool.ProduceRequestCallback() {
                public void onCompletion(Map<Integer, Long> partitionOffsets) {
                    ProduceResponse response = new ProduceResponse();
                    List<ProduceResponse.PartitionOffset> offsets = new Vector<ProduceResponse.PartitionOffset>();
                    for(Map.Entry<Integer, Long> partOff : partitionOffsets.entrySet()) {
                        offsets.add(new ProduceResponse.PartitionOffset(partOff.getKey(), partOff.getValue()));
                    }
                    response.setOffsets(offsets);
                    asyncResponse.resume(response);
                }

                public void onException(Exception e) {
                    asyncResponse.resume(e);
                }
            });

        }
        catch (IllegalArgumentException e) {
            // FIXME Thrown when base64 decoding fails; this should be handled by normal validation.
            throw new NotAcceptableException();
        }
    }
}