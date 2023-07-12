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

import io.emeraldpay.dshackle.config.ChainsConfig
import io.emeraldpay.dshackle.config.UpstreamsConfig
import io.emeraldpay.dshackle.reader.Reader
import io.emeraldpay.dshackle.test.ApiReaderMock
import io.emeraldpay.dshackle.test.TestingCommons
import io.emeraldpay.dshackle.upstream.Head
import io.emeraldpay.dshackle.upstream.rpcclient.JsonRpcResponse
import io.emeraldpay.etherjar.hex.HexData
import io.emeraldpay.etherjar.rpc.RpcResponseError
import io.emeraldpay.etherjar.rpc.json.TransactionCallJson
import io.emeraldpay.etherjar.domain.Address
import reactor.core.publisher.Mono
import reactor.util.function.Tuples
import spock.lang.Specification

import java.time.Duration

import static io.emeraldpay.dshackle.upstream.UpstreamAvailability.*

class EthereumUpstreamValidatorSpec extends Specification {

    def "Resolve to final availability"() {
        setup:
        def validator = new EthereumUpstreamValidator(Stub(EthereumLikeUpstream), UpstreamsConfig.PartialOptions.getDefaults().buildOptions())
        expect:
        validator.resolve(Tuples.of(sync, peers, call)) == exp
        where:
        exp         | sync          | peers       | call
        OK          | OK            | OK          | OK
        IMMATURE    | OK            | IMMATURE    | OK
        UNAVAILABLE | OK            | UNAVAILABLE | OK
        SYNCING     | SYNCING       | OK          | OK
        SYNCING     | SYNCING       | IMMATURE    | OK
        UNAVAILABLE | SYNCING       | UNAVAILABLE | OK
        UNAVAILABLE | UNAVAILABLE   | OK          | OK
        UNAVAILABLE | UNAVAILABLE   | IMMATURE    | OK
        UNAVAILABLE | UNAVAILABLE   | UNAVAILABLE | OK
    }

    def "Doesnt check eth_syncing when disabled"() {
        setup:
        def options = UpstreamsConfig.PartialOptions.getDefaults().tap {
            it.validateSyncing = false
        }.buildOptions()
        def up = Mock(EthereumLikeUpstream)
        def validator = new EthereumUpstreamValidator(up, options)

        when:
        def act = validator.validateSyncing().block(Duration.ofSeconds(1))
        then:
        act == OK
        0 * up.getIngressReader()
    }

    def "Syncing is OK when false returned from upstream"() {
        setup:
        def options = UpstreamsConfig.PartialOptions.getDefaults().tap {
            it.validateSyncing = true
        }.buildOptions()
        def up = TestingCommons.upstream(
                new ApiReaderMock().tap {
                    answer("eth_syncing", [], false)
                }
        )
        def validator = new EthereumUpstreamValidator(up, options)

        when:
        def act = validator.validateSyncing().block(Duration.ofSeconds(1))
        then:
        act == OK
    }

    def "Execute onSyncingNode with result of eth_syncing"() {
        setup:
        def options = UpstreamsConfig.PartialOptions.getDefaults().tap {
            it.validateSyncing = true
        }.buildOptions()
        def up = Mock(EthereumLikeUpstream) {
            2 * getIngressReader() >> Mock(Reader) { reader ->
                2 * reader.read(_) >>> [
                        Mono.just(new JsonRpcResponse('true'.getBytes(), null)),
                        Mono.just(new JsonRpcResponse('false'.getBytes(), null))
                ]
            }
            2 * getHead() >> Mock(Head) {head ->
                1 * head.onSyncingNode(true)
                1 * head.onSyncingNode(false)
            }
        }
        def validator = new EthereumUpstreamValidator(up, options)

        when:
        def act = validator.validateSyncing().block(Duration.ofSeconds(1))
        def act2 = validator.validateSyncing().block(Duration.ofSeconds(1))
        then:
        act == SYNCING
        act2 == OK
    }

    def "Syncing is SYNCING when state returned from upstream"() {
        setup:
        def options = UpstreamsConfig.PartialOptions.getDefaults().tap {
            it.validateSyncing = true
        }.buildOptions()
        def up = TestingCommons.upstream(
                new ApiReaderMock().tap {
                    answer("eth_syncing", [], [startingBlock: 100, currentBlock: 50])
                }
        )
        def validator = new EthereumUpstreamValidator(up, options)

        when:
        def act = validator.validateSyncing().block(Duration.ofSeconds(1))
        then:
        act == SYNCING
    }

    def "Syncing is UNAVAILABLE when error returned from upstream"() {
        setup:
        def options = UpstreamsConfig.PartialOptions.getDefaults().tap {
            it.validateSyncing = true
        }.buildOptions()
        def up = TestingCommons.upstream(
                new ApiReaderMock().tap {
                    answer("eth_syncing", [], new RpcResponseError(RpcResponseError.CODE_METHOD_NOT_EXIST, "Unavailable"))
                }
        )
        def validator = new EthereumUpstreamValidator(up, options)

        when:
        def act = validator.validateSyncing().block(Duration.ofSeconds(1))
        then:
        act == UNAVAILABLE
    }

    def "Doesnt validate peers when disabled"() {
        setup:
        def options = UpstreamsConfig.PartialOptions.getDefaults().tap {
            it.validatePeers = false
            it.minPeers = 10
        }.buildOptions()
        def up = Mock(EthereumLikeUpstream)
        def validator = new EthereumUpstreamValidator(up, options)

        when:
        def act = validator.validatePeers().block(Duration.ofSeconds(1))
        then:
        act == OK
        0 * up.getApi()
    }

    def "Doesnt validate peers when zero peers is expected"() {
        setup:
        def options = UpstreamsConfig.PartialOptions.getDefaults().tap {
            it.validatePeers = true
            it.minPeers = 0
        }.buildOptions()
        def up = Mock(EthereumLikeUpstream)
        def validator = new EthereumUpstreamValidator(up, options)

        when:
        def act = validator.validatePeers().block(Duration.ofSeconds(1))
        then:
        act == OK
        0 * up.getIngressReader()
    }

    def "Peers is IMMATURE when state returned too few peers"() {
        setup:
        def options = UpstreamsConfig.PartialOptions.getDefaults().tap {
            it.validatePeers = true
            it.minPeers = 10
        }.buildOptions()
        def up = TestingCommons.upstream(
                new ApiReaderMock().tap {
                    answer("net_peerCount", [], "0x5")
                }
        )
        def validator = new EthereumUpstreamValidator(up, options)

        when:
        def act = validator.validatePeers().block(Duration.ofSeconds(1))
        then:
        act == IMMATURE
    }

    def "Peers is OK when state returned exactly min peers"() {
        setup:
        def options = UpstreamsConfig.PartialOptions.getDefaults().tap {
            it.validatePeers = true
            it.minPeers = 10
        }.buildOptions()
        def up = TestingCommons.upstream(
                new ApiReaderMock().tap {
                    answer("net_peerCount", [], "0xa")
                }
        )
        def validator = new EthereumUpstreamValidator(up, options)

        when:
        def act = validator.validatePeers().block(Duration.ofSeconds(1))
        then:
        act == OK
    }

    def "Peers is OK when state returned more than enough peers"() {
        setup:
        def options = UpstreamsConfig.PartialOptions.getDefaults().tap {
            it.validatePeers = true
            it.minPeers = 10
        }.buildOptions()
        def up = TestingCommons.upstream(
                new ApiReaderMock().tap {
                    answer("net_peerCount", [], "0xff")
                }
        )
        def validator = new EthereumUpstreamValidator(up, options)

        when:
        def act = validator.validatePeers().block(Duration.ofSeconds(1))
        then:
        act == OK
    }

    def "Peers is UNAVAILABLE when state returned error"() {
        setup:
        def options = UpstreamsConfig.PartialOptions.getDefaults().tap {
            it.validatePeers = true
            it.minPeers = 10
        }.buildOptions()
        def up = TestingCommons.upstream(
                new ApiReaderMock().tap {
                    answer("net_peerCount", [], new RpcResponseError(RpcResponseError.CODE_METHOD_NOT_EXIST, "Unavailable"))
                }
        )
        def validator = new EthereumUpstreamValidator(up, options)

        when:
        def act = validator.validatePeers().block(Duration.ofSeconds(1))
        then:
        act == UNAVAILABLE
    }

    def "Doesnt check call limit when disabled"() {
        setup:
        def options = UpstreamsConfig.PartialOptions.getDefaults().tap {
            it.validateCalllimit = false
        }.buildOptions()
        def up = Mock(EthereumLikeUpstream)
        def validator = new EthereumUpstreamValidator(up, options)

        when:
        def act = validator.validateCallLimit().block(Duration.ofSeconds(1))
        then:
        act == OK
        0 * up.getIngressReader()
    }

    def "Upstream available if not error from call limit check"() {
        setup:
        def options = UpstreamsConfig.PartialOptions.getDefaults().buildOptions()
        def up = TestingCommons.upstream(
                new ApiReaderMock().tap {
                    answerOnce("eth_call", [new TransactionCallJson(
                            Address.from("0x32268860cAAc2948Ab5DdC7b20db5a420467Cf96"),
                            HexData.from("0xd8a26e3a0000000000000000000000000000000000000000000000000000000000030ce0")
                    ), "latest"], "0x00000000000000000000")
                }
        )
        def validator = new EthereumUpstreamValidator(up, options, "0x32268860cAAc2948Ab5DdC7b20db5a420467Cf96")

        when:
        def act = validator.validateCallLimit().block(Duration.ofSeconds(1))
        then:
        act == OK
        when:
        def act2 = validator.validateCallLimit().block(Duration.ofSeconds(1))
        then:
        act2 == OK
    }

    def "Upstream not available if error returned on call limit check"() {
        setup:
        def options = UpstreamsConfig.PartialOptions.getDefaults().buildOptions()
        def up = TestingCommons.upstream(
                new ApiReaderMock().tap {
                    answer("eth_call", [new TransactionCallJson(
                            Address.from("0x32268860cAAc2948Ab5DdC7b20db5a420467Cf96"),
                            HexData.from("0xd8a26e3a0000000000000000000000000000000000000000000000000000000000030ce0")
                    ), "latest"], new RpcResponseError(RpcResponseError.CODE_INVALID_REQUEST, "Too long"))
                }
        )
        def validator = new EthereumUpstreamValidator(up, options, "0x32268860cAAc2948Ab5DdC7b20db5a420467Cf96")

        when:
        def act = validator.validateCallLimit().block(Duration.ofSeconds(1))
        then:
        act == UNAVAILABLE
        when:
        def act2 = validator.validateCallLimit().block(Duration.ofSeconds(1))
        then:
        act2 == UNAVAILABLE
    }

}
