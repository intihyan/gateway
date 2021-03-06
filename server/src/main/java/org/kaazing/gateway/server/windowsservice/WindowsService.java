/**
 * Copyright (c) 2007-2014 Kaazing Corporation. All rights reserved.
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.kaazing.gateway.server.windowsservice;


/**
 * JNA-based Windows Service program, based on the example I found in http://enigma2eureka.blogspot
 * .com/2011/05/writing-windows-service-in-java.html
 * and my own C++-based service work from earlier in 3.2 and 3.3.
 * <p/>
 * NOTE: if it isn't already obvious, this WindowsService is NOT related in any way to the Kaazing 'service' objects, which
 * implement Kaazing services within the Gateway.
 */
public class WindowsService implements ISimpleService {

    @Override
    public int run(String[] args) {
        return 0;   // XXX FIX THIS!!!
    }

    @Override
    public void stop() {
    }

    public static void main(String[] args) {
        SimpleServiceManager.runSimpleService(new WindowsService());
    }
}
