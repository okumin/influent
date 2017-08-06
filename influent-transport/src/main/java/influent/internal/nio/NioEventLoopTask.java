/*
 * Copyright 2016 okumin
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

package influent.internal.nio;

import java.io.IOException;
import java.nio.channels.CancelledKeyException;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.IllegalBlockingModeException;
import java.nio.channels.IllegalSelectorException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Iterator;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.IntUnaryOperator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import influent.internal.util.Exceptions;

/**
 * Tasks of {@code NioEventLoop}.
 */
interface NioEventLoopTask {
  /**
   * Registers a new channel.
   */
  final class Register implements NioEventLoopTask {
    private static final Logger logger = LoggerFactory.getLogger(Register.class);

    private final Selector selector;
    private final SelectableChannel channel;
    private final int ops;
    private final NioAttachment attachment;

    Register(final Selector selector,
             final SelectableChannel channel,
             final int ops,
             final NioAttachment attachment) {
      this.selector = selector;
      this.channel = channel;
      this.ops = ops;
      this.attachment = attachment;
    }

    @Override
    public void run() {
      try {
        channel.configureBlocking(false).register(selector, ops, attachment);
      } catch (final ClosedSelectorException
          | IllegalBlockingModeException
          | IllegalSelectorException e) {
        throw new AssertionError(e);
      } catch (final CancelledKeyException | IllegalArgumentException | IOException e) {
        // ClosedChannelException is an IOException
        logger.error("NioEventLoopTask.Register with " + attachment + " threw an exception.", e);
      }
    }
  }

  /**
   * Updates an interest set.
   */
  final class UpdateInterestSet implements NioEventLoopTask {
    private static final Logger logger = LoggerFactory.getLogger(UpdateInterestSet.class);

    private final SelectionKey key;
    private final IntUnaryOperator updater;

    UpdateInterestSet(final SelectionKey key, final IntUnaryOperator updater) {
      this.key = key;
      this.updater = updater;
    }

    @Override
    public void run() {
      try {
        final int current = key.interestOps();
        final int updated = updater.applyAsInt(current);
        if (updated != current) {
          key.interestOps(updated);
        }
      } catch (final CancelledKeyException | IllegalArgumentException e) {
        logger.error("UpdateInterestSet threw an exception.", e);
      }
    }
  }

  /**
   * Selects and proceeds IO operations.
   */
  final class Select implements NioEventLoopTask {
    private static final Logger logger = LoggerFactory.getLogger(Select.class);

    private final Selector selector;

    Select(final Selector selector) {
      this.selector = selector;
    }

    @Override
    public void run() {
      final int ready = select();
      if (ready == 0) {
        return;
      }

      final Set<SelectionKey> keys = selectedKeys();
      final Iterator<SelectionKey> iterator = keys.iterator();

      while (iterator.hasNext()) {
        final SelectionKey key = iterator.next();
        final NioAttachment attachment = (NioAttachment) key.attachment();
        logger.debug("Selected key for {}", attachment);

        try {
          if (key.isWritable()) {
            attachment.onWritable(key);
          }
          if (key.isReadable()) {
            attachment.onReadable(key);
          }
          if (key.isAcceptable()) {
            attachment.onAcceptable(key);
          }
          if (key.isConnectable()) {
            attachment.onConnectable(key);
          }
        } catch (final CancelledKeyException e) {
          logger.debug("The key has already been cancelled.");
          Exceptions.ignore(attachment::close, "Failed closing " + attachment);
        } catch (final Exception e) {
          logger.debug("An error occurred when handling an event.", e);
          Exceptions.ignore(attachment::close, "Failed closing " + attachment);
        }

        iterator.remove();
      }
    }

    private int select() {
      try {
        return selector.select();
      } catch (final ClosedSelectorException e) {
        throw new AssertionError(e);
      } catch (final IOException e) {
        logger.error("`select` failed.", e);
        return 0;
      }
    }

    private Set<SelectionKey> selectedKeys() {
      try {
        return selector.selectedKeys();
      } catch (final ClosedSelectorException e) {
        throw new AssertionError(e);
      }
    }
  }

  /**
   * Executes this task.
   */
  void run();
}
