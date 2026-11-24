package dev.nezz.chainledger.node.store;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TransactionRepository extends JpaRepository<TransactionEntity, String> {

    @Query("""
            select t from TransactionEntity t join fetch t.block b
            where t.sender = :address or t.recipient = :address
            order by b.index desc, t.position desc""")
    List<TransactionEntity> history(@Param("address") String address);
}
