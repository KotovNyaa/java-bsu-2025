package com.bank.core.engine;

import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.WaitStrategy;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import com.lmax.disruptor.util.DaemonThreadFactory;

import com.bank.core.engine.consumers.BusinessLogicConsumer;
import com.bank.core.engine.consumers.JournalingConsumer;
import com.bank.core.engine.consumers.ReplicationConsumer;

/**
 * Центральный класс, который настраивает и запускает конвейер
 */

public class TransactionRingBuffer {

    private final Disruptor<TransactionEvent> disruptor;
    private final RingBuffer<TransactionEvent> ringBuffer;

    public TransactionRingBuffer(
            WaitStrategy waitStrategy,
            JournalingConsumer journalingConsumer,
            ReplicationConsumer replicationConsumer,
            BusinessLogicConsumer businessLogicConsumer) {

        this.disruptor = new Disruptor<>(
                TransactionEvent::new,
                1024 * 16,
                DaemonThreadFactory.INSTANCE,
                ProducerType.MULTI,
                waitStrategy);

        this.ringBuffer = disruptor.getRingBuffer();
    }

    public void start() {
        disruptor.start();
    }

    public void stop() {
        disruptor.shutdown();
    }

    public RingBuffer<TransactionEvent> getRingBuffer() {
        return ringBuffer;
    }

    public Disruptor<TransactionEvent> getDisruptor() {
        return disruptor;
    }
}
