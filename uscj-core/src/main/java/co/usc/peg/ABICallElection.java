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

package co.usc.peg;

import co.usc.core.UscAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Representation of a given state of the election
 * of an ABI function call by a series of known
 * and authorized electors.
 *
 * @author Ariel Mendelzon
 */
public class ABICallElection {
    private static final Logger logger = LoggerFactory.getLogger("ABICallElection");
    private AddressBasedAuthorizer authorizer;
    private Map<ABICallSpec, List<UscAddress>> votes;

    public ABICallElection(AddressBasedAuthorizer authorizer, Map<ABICallSpec, List<UscAddress>> votes) {
        this.authorizer = authorizer;
        this.votes = votes;
        validate();
    }

    public ABICallElection(AddressBasedAuthorizer authorizer) {
        this.authorizer = authorizer;
        this.votes = new HashMap<>();
    }

    public Map<ABICallSpec, List<UscAddress>> getVotes() {
        return votes;
    }

    public void clear() {
        this.votes = new HashMap<>();
    }

    /**
     * Register voter's vote for callSpec
     * @param callSpec the call spec the voter is voting for
     * @param voter the voter's key
     * @return whether the voting succeeded
     */
    public boolean vote(ABICallSpec callSpec, UscAddress voter) {
        if (!authorizer.isAuthorized(voter)) {
            logger.warn("Failed to register vote: Unauthorized voter: {}", voter.toString());
            return false;
        }

        if (!votes.containsKey(callSpec)) {
            votes.put(callSpec, new ArrayList<>());
        }

        List<UscAddress> callVoters = votes.get(callSpec);

        if (callVoters.contains(voter)) {
            logger.warn("Vote from this address {} has already being registered", voter.toString());
            return false;
        }

        callVoters.add(voter);
        return true;
    }

    /**
     * Returns the election winner abi call spec, or null if there's none
     * The vote authorizer determines the number of participants,
     * whereas this class determines the number of votes that
     * conforms a win
     * @return the winner abi call spec
     */
    public ABICallSpec getWinner() {
        for (Map.Entry<ABICallSpec, List<UscAddress>> specVotes : votes.entrySet()) {
            if (specVotes.getValue().size() >= authorizer.getRequiredAuthorizedKeys()) {
                return specVotes.getKey();
            }
        }

        return null;
    }

    /**
     * Removes the entry votes for the current winner of the election
     */
    public void clearWinners() {
        ABICallSpec winner = getWinner();
        if (winner != null) {
            votes.remove(winner);
        }
    }

    public void clearWinners(ABICallSpec winner) {
        if (winner != null) {
            votes.remove(winner);
        }
    }

    private void validate() {
        // Make sure all the votes are authorized
        for (Map.Entry<ABICallSpec, List<UscAddress>> specVotes : votes.entrySet()) {
            for (UscAddress vote : specVotes.getValue()) {
                if (!authorizer.isAuthorized(vote)) {
                    throw new RuntimeException("Unauthorized voter");
                }
            }
        }
    }
}
