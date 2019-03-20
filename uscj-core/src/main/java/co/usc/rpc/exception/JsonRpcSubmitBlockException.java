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

package co.usc.rpc.exception;

import org.ethereum.rpc.exception.UscJsonRpcRequestException;

/**
 * Designed to be used only when an error occurs at BpServer.SubmitUlordBlock() method.
 *
 * @author martin.medina
 */
public class JsonRpcSubmitBlockException extends UscJsonRpcRequestException {

    public JsonRpcSubmitBlockException(String message) {
        super(JsonRpcApplicationDefinedErrorCodes.SUBMIT_BLOCK, message);
    }
}
