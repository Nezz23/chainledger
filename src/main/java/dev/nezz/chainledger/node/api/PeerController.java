package dev.nezz.chainledger.node.api;

import dev.nezz.chainledger.node.NodeService;
import jakarta.validation.Valid;
import java.util.Set;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/peers")
public class PeerController {

    private final NodeService node;

    public PeerController(NodeService node) {
        this.node = node;
    }

    @GetMapping
    public Set<String> peers() {
        return node.peers();
    }

    /** Registers a peer and immediately syncs with it, so a fresh node catches up in one call. */
    @PostMapping
    public Set<String> add(@Valid @RequestBody Requests.Peer request) {
        node.addPeer(request.url());
        node.resolveConsensus();
        return node.peers();
    }
}
