/*
 * Copyright (C) 2020 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.google.cloud.teleport.v2.templates;

import static com.google.cloud.teleport.v2.kafka.consumer.Utils.getKafkaCredentialsFromVault;
import static com.google.cloud.teleport.v2.templates.KafkaPubsubConstants.PASSWORD;
import static com.google.cloud.teleport.v2.templates.KafkaPubsubConstants.USERNAME;
import static com.google.cloud.teleport.v2.transforms.FormatTransform.readFromKafka;

import com.google.cloud.teleport.v2.kafka.consumer.Utils;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.apache.beam.runners.direct.DirectOptions;
import org.apache.beam.sdk.PipelineResult;
import org.apache.beam.sdk.io.gcp.pubsub.PubsubIO;
import org.apache.beam.sdk.io.gcp.pubsub.TestPubsubSignal;
import org.apache.beam.sdk.testing.TestPipeline;
import org.apache.beam.sdk.transforms.DoFn;
import org.apache.beam.sdk.transforms.Values;
import org.apache.beam.sdk.values.KV;
import org.apache.beam.sdk.values.PCollection;
import org.apache.beam.vendor.guava.v26_0_jre.com.google.common.base.Supplier;
import org.apache.kafka.common.config.SaslConfigs;
import org.apache.kafka.common.security.scram.internals.ScramMechanism;
import org.joda.time.Duration;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/**
 * Test class for {@link KafkaToPubsub}.
 */
public class KafkaToPubsubTest {

  @Rule
  public final transient TestPipeline pipeline = TestPipeline.create();
  @Rule
  public transient TestPubsubSignal signal = TestPubsubSignal.create();

  private static class LogIt extends DoFn<String, String> {

    @ProcessElement
    public void process(@Element String context, OutputReceiver<String> out) {
      System.out.println(context);
      out.output(context);
    }
  }

  @Before
  public void setUp() {

//    SetupPubsubContainer psc = new SetupPubsubContainer();
  }

  @Test
  public void testKafkaToPubsubE2E() throws IOException {
    pipeline.getOptions().as(DirectOptions.class).setBlockOnRun(false);

    RunKafkaContainer rkc = new RunKafkaContainer();
    String bootstrapServer = rkc.getBootstrapServer();
    String[] kafkaTopicsList = new String[]{rkc.getTopicName()};

    String pubsubTopicPath = SetupPubsubContainer.getTopicPath();

    Map<String, Object> kafkaConfig = new HashMap<>();
    Map<String, String> sslConfig = null;

    PCollection<KV<String, String>> readStrings = pipeline
        .apply("readFromKafka",
            readFromKafka(bootstrapServer, Arrays.asList(kafkaTopicsList), kafkaConfig, sslConfig));

    PCollection<String> readFromPubsub = readStrings.apply(Values.create())
        .apply("writeToPubSub", PubsubIO.writeStrings().to(pubsubTopicPath)).getPipeline()
        .apply("readFromPubsub",
            PubsubIO.readStrings().fromTopic(pubsubTopicPath));
//    PCollection<String> latest = readFromPubsub.apply("printIt", ParDo.of(new LogIt()));

    readFromPubsub.apply(
        "waitForAnyMessage",
        signal.signalSuccessWhen(readFromPubsub.getCoder(), anyMessages -> true));

    Supplier<Void> start = signal.waitForStart(Duration.standardSeconds(10));
    pipeline.apply(signal.signalStart());
    PipelineResult job = pipeline.run();
    System.out.println("Not blocked");
    start.get();

    System.out.println("Waiting for success...");
    signal.waitForSuccess(Duration.standardMinutes(2));
    try {
      job.cancel();
    } catch (IOException exc) {
      System.out.println("IOException");
    } catch (UnsupportedOperationException e) {
      System.out.println("UnsupportedOperationException");
    }

//    pipeline.run().waitUntilFinish(Duration.standardSeconds(10));
  }


  /**
   * Tests configureKafka() with a null input properties.
   */
  @Test
  public void testConfigureKafkaNullProps() {
    Map<String, Object> config = Utils.configureKafka(null);
    Assert.assertEquals(config, new HashMap<>());
  }

  /**
   * Tests configureKafka() without a Password in input properties.
   */
  @Test
  public void testConfigureKafkaNoPassword() {
    Map<String, String> props = new HashMap<>();
    props.put(USERNAME, "username");
    Map<String, Object> config = Utils.configureKafka(props);
    Assert.assertEquals(config, new HashMap<>());
  }

  /**
   * Tests configureKafka() without a Username in input properties.
   */
  @Test
  public void testConfigureKafkaNoUsername() {
    Map<String, String> props = new HashMap<>();
    props.put(PASSWORD, "password");
    Map<String, Object> config = Utils.configureKafka(props);
    Assert.assertEquals(config, new HashMap<>());
  }

  /**
   * Tests configureKafka() with an appropriate input properties.
   */
  @Test
  public void testConfigureKafka() {
    Map<String, String> props = new HashMap<>();
    props.put(USERNAME, "username");
    props.put(PASSWORD, "password");

    Map<String, Object> expectedConfig = new HashMap<>();
    expectedConfig.put(SaslConfigs.SASL_MECHANISM, ScramMechanism.SCRAM_SHA_512.mechanismName());
    expectedConfig.put(
        SaslConfigs.SASL_JAAS_CONFIG,
        String.format(
            "org.apache.kafka.common.security.scram.ScramLoginModule required "
                + "username=\"%s\" password=\"%s\";",
            props.get(USERNAME), props.get(PASSWORD)));

    Map<String, Object> config = Utils.configureKafka(props);
    Assert.assertEquals(config, expectedConfig);
  }

  /**
   * Tests getKafkaCredentialsFromVault() with an invalid url.
   */
  @Test
  public void testGetKafkaCredentialsFromVaultInvalidUrl() {
    Map<String, Map<String, String>> credentials =
        getKafkaCredentialsFromVault("some-url", "some-token");
    Assert.assertEquals(credentials, new HashMap<>());
  }
}