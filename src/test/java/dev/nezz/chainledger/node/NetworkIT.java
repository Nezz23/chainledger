package dev.nezz.chainledger.node;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestClient;

/** Three real nodes over HTTP: gossip, block propagation, and fork resolution by total work. */
class NetworkIT {

    private static final List<String> SCHEMAS = List.of("net_a", "net_b", "net_c", "net_d");
    private final RestClient http = RestClient.create();
    private ConfigurableApplicationContext a;
    private ConfigurableApplicationContext b;
    private ConfigurableApplicationContext c;
    private String urlA;
    private String urlB;
    private String urlC;

    @BeforeEach
    void startNetwork() {
        SCHEMAS.forEach(TestNodes::resetSchema);
        a = TestNodes.start("node-a", "net_a");
        b = TestNodes.start("node-b", "net_b");
        c = TestNodes.start("node-c", "net_c");
        urlA = TestNodes.url(a);
        urlB = TestNodes.url(b);
        urlC = TestNodes.url(c);
        connect(urlA, urlB, urlC);
        connect(urlB, urlA, urlC);
        connect(urlC, urlA, urlB);
    }

    @AfterEach
    void stop() {
        for (ConfigurableApplicationContext node : new ConfigurableApplicationContext[]{a, b, c}) {
            if (node != null) {
                node.close();
            }
        }
    }

    @Test
    void transactionsAndBlocksPropagateAndAllNodesAgreeOnBalances() {
        String alice = post(urlA + "/api/wallets", null).get("address").toString();
        String bob = post(urlB + "/api/wallets", null).get("address").toString();

        post(urlA + "/api/mine", Map.of("minerAddress", alice));
        assertAllAtHeight(1);

        post(urlA + "/api/wallets/" + alice + "/transfers", Map.of("to", bob, "amount", 30, "fee", 1));
        for (String url : List.of(urlA, urlB, urlC)) {
            assertThat(get(url + "/api/transactions/pending", List.class)).hasSize(1);
        }

        post(urlC + "/api/mine", Map.of("minerAddress", "c0ffee"));
        assertAllAtHeight(2);
        for (String url : List.of(urlA, urlB, urlC)) {
            assertThat(balance(url, alice)).isEqualTo(19);
            assertThat(balance(url, bob)).isEqualTo(30);
            assertThat(balance(url, "c0ffee")).isEqualTo(51);
            assertThat(get(url + "/api/chain/audit", Map.class).get("valid")).isEqualTo(true);
        }
        assertThat(get(urlB + "/api/addresses/" + bob + "/transactions", List.class)).hasSize(1);
    }

    @Test
    void anIsolatedNodeWithMoreWorkWinsWhenItJoins() {
        post(urlA + "/api/mine", Map.of("minerAddress", "aaaa"));
        assertAllAtHeight(1);

        ConfigurableApplicationContext d = TestNodes.start("node-d", "net_d");
        try {
            String urlD = TestNodes.url(d);
            for (int i = 0; i < 3; i++) {
                post(urlD + "/api/mine", Map.of("minerAddress", "dddd"));
            }
            // A learns about D: D's 3 blocks outweigh A's 1, so A switches; B and C follow when D's next block lands
            addPeer(urlA, urlD);
            addPeer(urlD, urlA);
            post(urlD + "/api/mine", Map.of("minerAddress", "dddd"));

            String tip = summary(urlD).get("tipHash").toString();
            for (String url : List.of(urlA, urlB, urlC, urlD)) {
                assertThat(summary(url).get("tipHash")).isEqualTo(tip);
                assertThat(balance(url, "aaaa")).isZero();
                assertThat(balance(url, "dddd")).isEqualTo(200);
            }
        } finally {
            d.close();
        }
    }

    private void connect(String node, String... peers) {
        for (String peer : peers) {
            addPeer(node, peer);
        }
    }

    private void addPeer(String node, String peer) {
        http.post().uri(node + "/api/peers").body(Map.of("url", peer)).retrieve().toBodilessEntity();
    }

    private void assertAllAtHeight(int height) {
        String tip = summary(urlA).get("tipHash").toString();
        for (String url : List.of(urlA, urlB, urlC)) {
            Map<?, ?> summary = summary(url);
            assertThat(((Number) summary.get("height")).intValue()).isEqualTo(height);
            assertThat(summary.get("tipHash")).isEqualTo(tip);
        }
    }

    private Map<?, ?> summary(String url) {
        return get(url + "/api/chain", Map.class);
    }

    private long balance(String url, String address) {
        return ((Number) get(url + "/api/addresses/" + address, Map.class).get("balance")).longValue();
    }

    private <T> T get(String url, Class<T> type) {
        return http.get().uri(url).retrieve().body(type);
    }

    private Map<String, Object> post(String url, Object body) {
        RestClient.RequestBodySpec request = http.post().uri(url);
        if (body != null) {
            request.body(body);
        }
        return request.retrieve().body(new ParameterizedTypeReference<>() {
        });
    }
}
