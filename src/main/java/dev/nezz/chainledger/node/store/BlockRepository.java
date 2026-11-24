package dev.nezz.chainledger.node.store;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface BlockRepository extends JpaRepository<BlockEntity, Long> {

    /** One query for the whole chain instead of one per block (avoids the N+1 problem on startup). */
    @Query("select distinct b from BlockEntity b left join fetch b.transactions order by b.index")
    List<BlockEntity> findChainWithTransactions();
}
