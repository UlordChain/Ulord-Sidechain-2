/*
 * This file is part of USC
 * Copyright (C) 2016 - 2018 USC developer team.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package co.usc.rpc.modules.eth.subscribe;

import org.ethereum.core.Block;
import org.ethereum.rpc.TypeConverter;

/**
 * The block header DTO for JSON serialization purposes.
 */
public class BlockHeaderNotification {
    private final String extraData;
    private final String gasLimit;
    private final String gasUsed;
    private final String logsBloom;
    private final String blockProducer;
    private final String number;
    private final String parentHash;
    private final String receiptsRoot;
    private final String stateRoot;
    private final String timestamp;
    private final String transactionsRoot;
    private final String hash;

    public BlockHeaderNotification(Block block) {
        extraData = TypeConverter.toJsonHex(block.getExtraData());
        gasLimit = TypeConverter.toJsonHex(block.getGasLimit());
        gasUsed = TypeConverter.toJsonHex(block.getGasUsed());
        logsBloom = TypeConverter.toJsonHex(block.getLogBloom());
        blockProducer = TypeConverter.toJsonHex(block.getCoinbase().getBytes());
        number = TypeConverter.toJsonHex(block.getNumber());
        parentHash = block.getParentHashJsonString();
        receiptsRoot = TypeConverter.toJsonHex(block.getReceiptsRoot());
        stateRoot = TypeConverter.toJsonHex(block.getStateRoot());
        timestamp = TypeConverter.toJsonHex(block.getTimestamp());
        transactionsRoot = TypeConverter.toJsonHex(block.getTxTrieRoot());
        hash = block.getHashJsonString();
    }

    /*
    public String getDifficulty() {
        return difficulty;
    }
    */

    public String getExtraData() {
        return extraData;
    }

    public String getGasLimit() {
        return gasLimit;
    }

    public String getGasUsed() {
        return gasUsed;
    }

    public String getLogsBloom() {
        return logsBloom;
    }

    public String getBlockProducer() {
        return blockProducer;
    }

    public String getNumber() {
        return number;
    }

    public String getParentHash() {
        return parentHash;
    }

    public String getReceiptsRoot() {
        return receiptsRoot;
    }

    /*
    public String getSha3Uncles() {
        return sha3Uncles;
    }
    */

    public String getStateRoot() {
        return stateRoot;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public String getTransactionsRoot() {
        return transactionsRoot;
    }

    public String getHash() {
        return hash;
    }
}
