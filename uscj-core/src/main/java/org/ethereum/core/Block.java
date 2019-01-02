/*
 * This file is part of USC
 * Copyright (C) 2016 - 2018 USC developer team.
 * (derived from ethereumJ library, Copyright (c) 2016 <ether.camp>)
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

package org.ethereum.core;

import co.usc.core.BlockDifficulty;
import co.usc.core.Coin;
import co.usc.core.UscAddress;
import co.usc.crypto.Keccak256;
import co.usc.panic.PanicProcessor;
import co.usc.remasc.RemascTransaction;
import co.usc.trie.Trie;
import co.usc.trie.TrieImpl;
import co.usc.ulordj.core.Address;
import co.usc.ulordj.core.NetworkParameters;
import co.usc.ulordj.params.RegTestParams;
import org.ethereum.crypto.Keccak256Helper;
import org.ethereum.rpc.TypeConverter;
import org.ethereum.util.RLP;
import org.ethereum.util.RLPElement;
import org.ethereum.util.RLPList;
import org.ethereum.vm.PrecompiledContracts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.bouncycastle.util.Arrays;
import org.bouncycastle.util.BigIntegers;
import org.bouncycastle.util.encoders.Hex;

import javax.annotation.Nonnull;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.Signature;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The block in Ethereum is the collection of relevant pieces of information
 * (known as the blockheader), H, together with information corresponding to
 * the comprised transactions, R, and a set of other blockheaders U that are known
 * to have a parent equalBytes to the present block’s parent’s parent
 * (such blocks are known as uncles).
 *
 * @author Roman Mandeleil
 * @author Nick Savers
 * @since 20.05.2014
 */
public class Block {

    private static final Logger logger = LoggerFactory.getLogger("block");
    private static final PanicProcessor panicProcessor = new PanicProcessor();

    private BlockHeader header;

    // The methods below make sure we use immutable lists
    /* Transactions */
    private List<Transaction> transactionsList;

    /* Block Producers Signatures*/
    private List<byte[]> signaturesList;

    /* Private */
    private byte[] rlpEncoded;
    private boolean parsed = false;

    /* Indicates if this block can or cannot be changed */
    private volatile boolean sealed;

    public Block(byte[] rawData) {
        this(rawData, true);
    }

    protected Block(byte[] rawData, boolean sealed) {
        this.rlpEncoded = rawData;
        this.sealed = sealed;
        parseRLP();
        // clear it so we always reencode the received data
        this.rlpEncoded = null;
    }

    public Block(BlockHeader header) {
        this.header = header;
        this.parsed = true;
    }

    public Block(BlockHeader header, List<Transaction> transactionsList, List<byte[]> signaturesList) {

        this(
                header.getParentHash().getBytes(),
                header.getCoinbase().getBytes(),
                header.getLogsBloom(),
                header.getNumber(),
                header.getGasLimit(),
                header.getGasUsed(),
                header.getTimestamp(),
                header.getExtraData(),
                header.getReceiptsRoot(),
                header.getTxTrieRoot(),
                header.getStateRoot(),
                transactionsList,
                header.getMinimumGasPrice() == null ? null : header.getMinimumGasPrice().getBytes(),
                header.getBpAddress(),
                signaturesList);
    }

    public Block(byte[] parentHash, byte[] coinbase, byte[] logsBloom,
                 long number, byte[] gasLimit,
                 long gasUsed, long timestamp, byte[] extraData,
                 byte[] receiptsRoot, byte[] transactionsRoot, byte[] stateRoot,
                 List<Transaction> transactionsList, byte[] minimumGasPrice, Address bpAddress,
                 List<byte[]> signaturesList) {

        this(parentHash, coinbase, logsBloom, number, gasLimit,
                gasUsed, timestamp, extraData, receiptsRoot, transactionsRoot,
                stateRoot, transactionsList, minimumGasPrice, Coin.ZERO, bpAddress, signaturesList);

        this.flushRLP();
    }

    public Block(byte[] parentHash, byte[] coinbase, byte[] logsBloom,
                 long number, byte[] gasLimit,
                 long gasUsed, long timestamp, byte[] extraData,
                 byte[] receiptsRoot, byte[] transactionsRoot, byte[] stateRoot,
                 List<Transaction> transactionsList, byte[] minimumGasPrice, Coin paidFees, Address bpAddress,
                 List<byte[]> signaturesList) {

        this(parentHash, coinbase, logsBloom, number, gasLimit,
                gasUsed, timestamp, extraData, transactionsList, minimumGasPrice, bpAddress, signaturesList);

        this.header.setPaidFees(paidFees);

        byte[] calculatedRoot = getTxTrie(transactionsList).getHash().getBytes();
        this.header.setTransactionsRoot(calculatedRoot);
        this.checkExpectedRoot(transactionsRoot, calculatedRoot);

        this.header.setStateRoot(stateRoot);
        this.header.setReceiptsRoot(receiptsRoot);
        this.header.setBpAddress(bpAddress);
        this.header.setSignatureRoot(getSignaturesHash(signaturesList));

        this.flushRLP();
    }

    public Block(byte[] parentHash, byte[] coinbase, byte[] logsBloom,
                 long number, byte[] gasLimit, long gasUsed, long timestamp,
                 byte[] extraData, List<Transaction> transactionsList, byte[] minimumGasPrice, Address bpAddress,
                 List<byte[]> signaturesList) {

        if (transactionsList == null) {
            this.transactionsList = Collections.emptyList();
        }
        else {
            this.transactionsList = Collections.unmodifiableList(transactionsList);
        }
        this.signaturesList = signaturesList;
        this.header = new BlockHeader(parentHash, coinbase, logsBloom,
                number, gasLimit, gasUsed,
                timestamp, extraData, minimumGasPrice, bpAddress);

        this.parsed = true;
    }

    public static Block fromValidData(BlockHeader header, List<Transaction> transactionsList) {
        Block block = new Block(header);
        block.transactionsList = transactionsList;
        block.seal();
        return block;
    }

    public void seal() {
        this.sealed = true;
        this.header.seal();
    }

    public boolean isSealed() {
        return this.sealed;
    }

    // Clone this block allowing modifications
    public Block cloneBlock() {
        return new Block(this.getEncoded(), false);
    }

    private void parseRLP() {
        RLPList block = RLP.decodeList(rlpEncoded);
        if (block.size() != 3) {
            throw new IllegalArgumentException("A block must have 3 exactly items");
        }

        // Parse Header
        RLPList header = (RLPList) block.get(0);

        // TODO: Find a way to pass current network parameters
        this.header = new BlockHeader(new RegTestParams(), header, this.sealed);

        // Parse Transactions
        RLPList txTransactions = (RLPList) block.get(1);
        this.transactionsList = parseTxs(txTransactions);
        byte[] calculatedRoot = getTxTrie(this.transactionsList).getHash().getBytes();
        this.checkExpectedRoot(this.header.getTxTrieRoot(), calculatedRoot);

        this.parsed = true;
    }

    // TODO(mc) remove this method and create a new ExecutedBlock class or similar
    public void setTransactionsList(@Nonnull List<Transaction> transactionsList) {
        /* A sealed block is immutable, cannot be changed */
        if (this.sealed) {
            throw new SealedBlockException("trying to alter transaction list");
        }

        this.transactionsList = Collections.unmodifiableList(transactionsList);
        rlpEncoded = null;
    }

    public BlockHeader getHeader() {
        if (!parsed) {
            parseRLP();
        }
        return this.header;
    }

    public Keccak256 getHash() {
        if (!parsed) {
            parseRLP();
        }
        return this.header.getHash();
    }

    public Keccak256 getParentHash() {
        if (!parsed) {
            parseRLP();
        }
        return this.header.getParentHash();
    }

    public UscAddress getCoinbase() {
        if (!parsed) {
            parseRLP();
        }
        return this.header.getCoinbase();
    }

    public byte[] getStateRoot() {
        if (!parsed) {
            parseRLP();
        }
        return this.header.getStateRoot();
    }

    public void setStateRoot(byte[] stateRoot) {
        /* A sealed block is immutable, cannot be changed */
        if (this.sealed) {
            throw new SealedBlockException("trying to alter state root");
        }

        if (!parsed) {
            parseRLP();
        }
        this.header.setStateRoot(stateRoot);
    }

    public byte[] getTxTrieRoot() {
        if (!parsed) {
            parseRLP();
        }
        return this.header.getTxTrieRoot();
    }

    public byte[] getReceiptsRoot() {
        if (!parsed) {
            parseRLP();
        }
        return this.header.getReceiptsRoot();
    }


    public byte[] getLogBloom() {
        if (!parsed) {
            parseRLP();
        }
        return this.header.getLogsBloom();
    }

    public Coin getFeesPaidToMiner() {
        if (!parsed) {
            parseRLP();
        }
        return this.header.getPaidFees();
    }

    public long getTimestamp() {
        if (!parsed) {
            parseRLP();
        }
        return this.header.getTimestamp();
    }

    public long getNumber() {
        if (!parsed) {
            parseRLP();
        }
        return this.header.getNumber();
    }

    public byte[] getGasLimit() {
        if (!parsed) {
            parseRLP();
        }
        return this.header.getGasLimit();
    }

    public long getGasUsed() {
        if (!parsed) {
            parseRLP();
        }
        return this.header.getGasUsed();
    }

    public byte[] getExtraData() {
        if (!parsed) {
            parseRLP();
        }
        return this.header.getExtraData();
    }

  public void setExtraData(byte[] data) {
      /* A sealed block is immutable, cannot be changed */
      if (this.sealed) {
          throw new SealedBlockException("trying to alter extra data");
      }

        this.header.setExtraData(data);
        rlpEncoded = null;
    }

    public List<Transaction> getTransactionsList() {
        if (!parsed) {
            parseRLP();
        }

        return Collections.unmodifiableList(this.transactionsList);
    }

    public Coin getMinimumGasPrice() {
        if (!parsed) {
            parseRLP();
        }
        return this.header.getMinimumGasPrice();
    }

    public List<byte[]> getSignaturesList() {
        return signaturesList;
    }

    public void setSignaturesList(List<byte[]> signaturesList) {
        this.signaturesList = signaturesList;
    }

    public void addSignature(byte[] signature) {
        this.signaturesList.add(signature);
    }

    private StringBuffer toStringBuff = new StringBuffer();
    // [parent_hash, coinbase, state_root, tx_trie_root,
    // number, minGasPrice, gasLimit, gasUsed, timestamp,
    // extradata]

    @Override
    public String toString() {
        if (!parsed) {
            parseRLP();
        }

        toStringBuff.setLength(0);
        toStringBuff.append(Hex.toHexString(this.getEncoded())).append("\n");
        toStringBuff.append("BlockData [ ");
        toStringBuff.append("hash=").append(this.getHash()).append("\n");
        toStringBuff.append(header.toString());

        if (!getTransactionsList().isEmpty()) {
            toStringBuff.append("Txs [\n");
            for (Transaction tx : getTransactionsList()) {
                toStringBuff.append(tx);
                toStringBuff.append("\n");
            }
            toStringBuff.append("]\n");
        } else {
            toStringBuff.append("Txs []\n");
        }
        toStringBuff.append("]");

        return toStringBuff.toString();
    }

    public String toFlatString() {
        if (!parsed) {
            parseRLP();
        }

        toStringBuff.setLength(0);
        toStringBuff.append("BlockData [");
        toStringBuff.append("hash=").append(this.getHash());
        toStringBuff.append(header.toFlatString());

        for (Transaction tx : getTransactionsList()) {
            toStringBuff.append("\n");
            toStringBuff.append(tx.toString());
        }

        toStringBuff.append("]");
        return toStringBuff.toString();
    }

    private List<Transaction> parseTxs(RLPList txTransactions) {
        List<Transaction> parsedTxs = new ArrayList<>();

        for (int i = 0; i < txTransactions.size(); i++) {
            RLPElement transactionRaw = txTransactions.get(i);
            Transaction tx = new ImmutableTransaction(transactionRaw.getRLPData());

            if (isRemascTransaction(tx, i, txTransactions.size())) {
                // It is the remasc transaction
                tx = new RemascTransaction(transactionRaw.getRLPData());
            }
            parsedTxs.add(tx);
        }

        return Collections.unmodifiableList(parsedTxs);
    }

    public static boolean isRemascTransaction(Transaction tx, int txPosition, int txsSize) {

        return isLastTx(txPosition, txsSize) && checkRemascAddress(tx) && checkRemascTxZeroValues(tx);
    }

    private static boolean isLastTx(int txPosition, int txsSize) {
        return txPosition == (txsSize - 1);
    }

    private static boolean checkRemascAddress(Transaction tx) {
        return PrecompiledContracts.REMASC_ADDR.equals(tx.getReceiveAddress());
    }

    private static boolean checkRemascTxZeroValues(Transaction tx) {
        if(null != tx.getData() || null != tx.getSignature()){
            return false;
        }

        return Coin.ZERO.equals(tx.getValue()) &&
                BigInteger.ZERO.equals(new BigInteger(1, tx.getGasLimit())) &&
                Coin.ZERO.equals(tx.getGasPrice());

    }

    private void checkExpectedRoot(byte[] expectedRoot, byte[] calculatedRoot) {
        if (!Arrays.areEqual(expectedRoot, calculatedRoot)) {
            logger.error("Transactions trie root validation failed for block #{}", this.header.getNumber());
            panicProcessor.panic("txroot", String.format("Transactions trie root validation failed for block %d %s", this.header.getNumber(), this.header.getHash()));
        }
    }

    /**
     * check if param block is son of this block
     *
     * @param block - possible a son of this
     * @return - true if this block is parent of param block
     */
    public boolean isParentOf(Block block) {
        return this.getHash().equals(block.getParentHash());
    }

    public boolean isGenesis() {
        if (!parsed) {
            parseRLP();
        }

        return this.header.isGenesis();
    }

    public boolean isEqual(Block block) {
        return this.getHash().equals(block.getHash());
    }

    public boolean fastEquals(Block block) {
        return block != null && this.getHash().equals(block.getHash());
    }

    private byte[] getTransactionsEncoded() {
        byte[][] transactionsEncoded = new byte[transactionsList.size()][];
        int i = 0;
        for (Transaction tx : transactionsList) {
            transactionsEncoded[i] = tx.getEncoded();
            ++i;
        }
        return RLP.encodeList(transactionsEncoded);
    }

    public byte[] getEncoded() {
        if (rlpEncoded == null) {
            byte[] header = this.header.getEncoded();

            List<byte[]> block = getBodyElements();
            block.add(0, header);
            byte[][] elements = block.toArray(new byte[block.size()][]);

            this.rlpEncoded = RLP.encodeList(elements);
        }
        return rlpEncoded;
    }

    public byte[] getEncodedBody() {
        List<byte[]> body = getBodyElements();
        byte[][] elements = body.toArray(new byte[body.size()][]);
        return RLP.encodeList(elements);
    }

    private List<byte[]> getBodyElements() {
        if (!parsed) {
            parseRLP();
        }

        byte[] transactions = getTransactionsEncoded();

        List<byte[]> body = new ArrayList<>();
        body.add(transactions);

        return body;
    }

    public String getShortHash() {
        if (!parsed) {
            parseRLP();
        }

        return header.getShortHash();
    }

    public String getParentShortHash() {
        if (!parsed) {
            parseRLP();
        }

        return header.getParentShortHash();
    }

    public String getShortDescr() {
        return "#" + getNumber() + " (" + getShortHash() + " <~ "
                + getParentShortHash() + ") Txs:" + getTransactionsList().size();
    }

    public String getHashJsonString() {
        return TypeConverter.toJsonHex(getHash().getBytes());
    }

    public String getParentHashJsonString() {
        return getParentHash().toJsonString();
    }

    public static Trie getTxTrie(List<Transaction> transactions){
        if (transactions == null) {
            return new TrieImpl();
        }

        Trie txsState = new TrieImpl();
        for (int i = 0; i < transactions.size(); i++) {
            Transaction transaction = transactions.get(i);
            txsState = txsState.put(RLP.encodeInt(i), transaction.getEncoded());
        }

        return txsState;
    }

    public static byte[] getSignaturesHash(List<byte[]> signaturesList) {
        ByteArrayOutputStream bs = new ByteArrayOutputStream();
        byte[] message;
        for (byte[] b : signaturesList) {
            try {
                bs.write(b);
            } catch (IOException e) {
                logger.error("Failed to Hash signatures");
            }
        }
        message = bs.toByteArray();

        return new Keccak256(Keccak256Helper.keccak256(message)).getBytes();
    }

    public BigInteger getGasLimitAsInteger() {
        return (this.getGasLimit() == null) ? null : BigIntegers.fromUnsignedByteArray(this.getGasLimit());
    }

    public void flushRLP() {
        this.rlpEncoded = null;
        this.parsed = true;
    }
}
