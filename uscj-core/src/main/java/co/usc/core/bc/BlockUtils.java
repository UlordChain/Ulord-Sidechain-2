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

package co.usc.core.bc;

import co.usc.crypto.Keccak256;
import co.usc.net.BlockStore;
import org.ethereum.core.Block;
import org.ethereum.core.BlockHeader;
import org.ethereum.core.Blockchain;
import org.ethereum.db.BlockInformation;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by ajlopez on 19/08/2016.
 */
public class BlockUtils {

    private BlockUtils() { }

    public static boolean blockInSomeBlockChain(Block block, Blockchain blockChain) {
        return blockInSomeBlockChain(block.getHash(), block.getNumber(), blockChain);
    }

    private static boolean blockInSomeBlockChain(Keccak256 blockHash, long blockNumber, Blockchain blockChain) {
        final List<BlockInformation> blockInformations = blockChain.getBlocksInformationByNumber(blockNumber);
        return blockInformations.stream().anyMatch(bi -> Arrays.equals(blockHash.getBytes(), bi.getHash()));
    }

    public static Set<Keccak256> unknownDirectAncestorsHashes(Block block, Blockchain blockChain, BlockStore store) {
        Set<Keccak256> hashes = Collections.singleton(block.getParentHash());
        return unknownAncestorsHashes(hashes, blockChain, store, false);
    }

    public static Set<Keccak256> unknownAncestorsHashes(Keccak256 blockHash, Blockchain blockChain, BlockStore store) {
        Set<Keccak256> hashes = Collections.singleton(blockHash);
        return unknownAncestorsHashes(hashes, blockChain, store, true);
    }

    public static Set<Keccak256> unknownAncestorsHashes(Set<Keccak256> hashesToProcess, Blockchain blockChain, BlockStore store, boolean withUncles) {
        Set<Keccak256> unknown = new HashSet<>();
        Set<Keccak256> hashes = hashesToProcess;

        while (!hashes.isEmpty()) {
            hashes = getNextHashes(hashes, unknown, blockChain, store, withUncles);
        }

        return unknown;
    }

    private static Set<Keccak256> getNextHashes(Set<Keccak256> previousHashes, Set<Keccak256> unknown, Blockchain blockChain, BlockStore store, boolean withUncles) {
        Set<Keccak256> nextHashes = new HashSet<>();
        for (Keccak256 hash : previousHashes) {
            if (unknown.contains(hash)) {
                continue;
            }

            Block block = blockChain.getBlockByHash(hash.getBytes());
            if (block == null) {
                block = store.getBlockByHash(hash.getBytes());
            }

            if (block == null) {
                unknown.add(hash);
                continue;
            }

            if (!block.isGenesis() && !blockInSomeBlockChain(block, blockChain)) {
                nextHashes.add(block.getParentHash());

                /*
                if (withUncles) {
                    for (BlockHeader uncleHeader : block.getUncleList()) {
                        nextHashes.add(uncleHeader.getHash());
                    }
                }
                */
            }
        }
        return nextHashes;
    }

    public static void addBlockToList(List<Block> blocks, Block block) {
        for (Block b : blocks) {
            if (b.getHash().equals(block.getHash())) {
                return;
            }
        }

        blocks.add(block);
    }

    public static List<Block> sortBlocksByNumber(List<Block> blocks) {
        return blocks.stream()
                .sorted(Comparator.comparingLong(Block::getNumber))
                .collect(Collectors.toList());
    }

}
