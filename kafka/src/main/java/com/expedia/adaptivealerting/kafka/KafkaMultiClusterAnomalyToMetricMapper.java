/*
 * Copyright 2018-2019 Expedia Group, Inc.
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
package com.expedia.adaptivealerting.kafka;

import com.expedia.adaptivealerting.anomdetect.AnomalyToMetricMapper;
import com.expedia.adaptivealerting.core.anomaly.AnomalyResult;
import com.expedia.adaptivealerting.core.data.MappedMetricData;
import com.expedia.adaptivealerting.kafka.util.ConfigUtil;
import com.expedia.metrics.MetricData;
import com.expedia.metrics.metrictank.MetricTankIdFactory;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.errors.WakeupException;

import java.util.Collections;

/**
 * Maps anomalies to metrics. Note that the input topic actually contains {@link MappedMetricData} rather than
 * {@link AnomalyResult}.
 */
@Slf4j
public class KafkaMultiClusterAnomalyToMetricMapper implements Runnable {
    private static final String APP_ID = "mc-a2m-mapper";
    private static final String ANOMALY_CONSUMER = "anomaly-consumer";
    private static final String METRIC_PRODUCER = "metric-producer";
    private static final String TOPIC = "topic";
    private static final long POLL_PERIOD = 1000L;
    
    private final AnomalyToMetricMapper mapper = new AnomalyToMetricMapper();
    
    // TODO Replace this with the non-MetricTank version. [WLW]
    private final MetricTankIdFactory metricTankIdFactory = new MetricTankIdFactory();
    
    @Getter
    private final Consumer<String, MappedMetricData> anomalyConsumer;
    
    @Getter
    private final Producer<String, MetricData> metricProducer;
    
    private String anomalyTopic;
    private String metricTopic;
    
    public static void main(String[] args) {
        buildKafkaMultiClusterAnomalyToMetricMapper().run();
    }

    static KafkaMultiClusterAnomalyToMetricMapper buildKafkaMultiClusterAnomalyToMetricMapper() {
        // TODO Refactor the loader such that it's not tied to Kafka Streams. [WLW]
        val config = new TypesafeConfigLoader(APP_ID).loadMergedConfig();

        val consumerConfig = config.getConfig(ANOMALY_CONSUMER);
        val consumerTopic = consumerConfig.getString(TOPIC);
        val consumerProps = ConfigUtil.toConsumerConfig(consumerConfig);
        val consumer = new KafkaConsumer<String, MappedMetricData>(consumerProps);

        val producerConfig = config.getConfig(METRIC_PRODUCER);
        val producerTopic = producerConfig.getString(TOPIC);
        val producerProps = ConfigUtil.toProducerConfig(producerConfig);
        val producer = new KafkaProducer<String, MetricData>(producerProps);

        return new KafkaMultiClusterAnomalyToMetricMapper(consumer, producer, consumerTopic, producerTopic);
    }

    public KafkaMultiClusterAnomalyToMetricMapper(
            Consumer<String, MappedMetricData> anomalyConsumer,
            Producer<String, MetricData> metricProducer,
            String anomalyTopic,
            String metricTopic) {
        
        this.anomalyConsumer = anomalyConsumer;
        this.metricProducer = metricProducer;
        this.anomalyTopic = anomalyTopic;
        this.metricTopic = metricTopic;
    }
    
    @Override
    public void run() {
        log.info("Starting KafkaMultiClusterAnomalyToMetricMapper");
        anomalyConsumer.subscribe(Collections.singletonList(anomalyTopic));
        
        // See Kafka: The Definitive Guide, pp. 86 ff.
        try {
            while (true) {
                try {
                    val anomalyRecords = anomalyConsumer.poll(POLL_PERIOD);
                    log.trace("Read {} anomalyRecords from topic={}", anomalyRecords.count(), anomalyTopic);
                    anomalyRecords.forEach(anomalyRecord -> {
                        val metricDataRecord = toMetricDataRecord(anomalyRecord);
                        if (metricDataRecord != null) {
                            metricProducer.send(metricDataRecord);
                        }
                    });
                } catch (WakeupException e) {
                    throw e;
                } catch (Exception e) {
                    log.error("Error processing records", e);
                }
            }
        } catch (WakeupException e) {
            // Ignore for shutdown
            log.info("anomalyConsumer is awake");
        } finally {
            anomalyConsumer.close();
            metricProducer.flush();
            metricProducer.close();
        }
    }

    private ProducerRecord<String, MetricData> toMetricDataRecord(
            ConsumerRecord<String, MappedMetricData> consumerRecord) {

        assert(consumerRecord != null);

        val mappedMetricData = consumerRecord.value();
        val metricData = mappedMetricData.getMetricData();
        val metricDef = metricData.getMetricDefinition();
        val tags = metricDef.getTags();
        val kv = tags.getKv();

        // IMPORTANT: This check avoids generating an infinite sequence of MetricDatas.
        // Without it, this mapper would generate new MetricDatas from its own outputs.
        if (kv.containsKey(AnomalyToMetricMapper.AA_DETECTOR_UUID)) {
            return null;
        }

        val anomalyResult = mappedMetricData.getAnomalyResult();
        val newMetricData = mapper.toMetricData(anomalyResult);

        if (newMetricData == null) {
            return null;
        }

        val newMetricDef = newMetricData.getMetricDefinition();

        // Calling metricTankIdFactory.getId() fails when the metric definition contains tags having values that are
        // null or empty, or contain semicolons. We do see this in production. Hence this check. Would be better though
        // if we can limit or eliminate such metric definitions since we'd like to avoid unnecessary exceptions.
        String newMetricId;
        try {
            newMetricId = metricTankIdFactory.getId(newMetricDef);
        } catch (IllegalArgumentException e) {
            log.warn("IllegalArgumentException: message={}, newMetricDef={}", e.getMessage(), newMetricDef);
            return null;
        }

        return new ProducerRecord<>(metricTopic, newMetricId, newMetricData);
    }
}
