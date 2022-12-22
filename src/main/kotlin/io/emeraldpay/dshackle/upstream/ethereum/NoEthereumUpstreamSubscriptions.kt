/**
 * Copyright (c) 2022 EmeraldPay, Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.emeraldpay.dshackle.upstream.ethereum

import io.emeraldpay.dshackle.upstream.NoUpstreamSubscriptions
import io.emeraldpay.dshackle.upstream.ethereum.subscribe.PendingTxesSource

class NoEthereumUpstreamSubscriptions : NoUpstreamSubscriptions(), EthereumUpstreamSubscriptions {

    companion object {
        @JvmStatic
        val DEFAULT = NoEthereumUpstreamSubscriptions()
    }

    override fun getPendingTxes(): PendingTxesSource? {
        return null
    }
}
