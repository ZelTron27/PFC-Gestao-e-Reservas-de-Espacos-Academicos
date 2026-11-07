package br.com.classholder.classholder.audit.repository;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Repository;

import br.com.classholder.classholder.audit.domain.AuditLog;
import br.com.classholder.classholder.audit.dto.AuditEntry;
import br.com.classholder.classholder.audit.dto.AuditSearchCriteria;
import br.com.classholder.classholder.audit.dto.AuditSearchCriteria.EntityRef;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

@Repository
public class AuditLogSearchRepository {

    private static final int IN_CHUNK_SIZE = 1000;

    @PersistenceContext
    private EntityManager entityManager;

    public List<AuditLog> search(AuditSearchCriteria criteria, int offset, int limit) {
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        CriteriaQuery<AuditLog> query = builder.createQuery(AuditLog.class);
        Root<AuditLog> root = query.from(AuditLog.class);
        query.select(root)
                .where(predicates(builder, root, criteria).toArray(new Predicate[0]))
                .orderBy(builder.desc(root.get("timestamp")), builder.desc(root.get("id")));
        return entityManager.createQuery(query).setFirstResult(offset).setMaxResults(limit).getResultList();
    }

    public long count(AuditSearchCriteria criteria) {
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> query = builder.createQuery(Long.class);
        Root<AuditLog> root = query.from(AuditLog.class);
        query.select(builder.count(root)).where(predicates(builder, root, criteria).toArray(new Predicate[0]));
        return entityManager.createQuery(query).getSingleResult();
    }

    public List<String> findDistinctActions() {
        return entityManager.createQuery("select distinct a.acao from AuditLog a order by a.acao", String.class)
                .getResultList();
    }

    private List<Predicate> predicates(CriteriaBuilder builder, Root<AuditLog> root, AuditSearchCriteria criteria) {
        List<Predicate> predicates = new ArrayList<>();
        if (criteria.from() != null) {
            predicates.add(builder.greaterThanOrEqualTo(root.get("timestamp"), criteria.from()));
        }
        if (criteria.to() != null) {
            predicates.add(builder.lessThan(root.get("timestamp"), criteria.to()));
        }
        if (criteria.userId() != null) {
            predicates.add(builder.or(
                    builder.equal(root.get("userId"), criteria.userId()),
                    builder.and(
                            builder.equal(root.get("entidadeTipo"), AuditEntry.USER_ENTITY_TYPE),
                            builder.equal(root.get("entidadeId"), criteria.userId()))));
        }
        if (criteria.action() != null && !criteria.action().isBlank()) {
            predicates.add(builder.equal(root.get("acao"), criteria.action()));
        }
        if (criteria.entities() != null && !criteria.entities().isEmpty()) {
            predicates.add(entityPredicate(builder, root, criteria.entities()));
        }
        return predicates;
    }

    private Predicate entityPredicate(CriteriaBuilder builder, Root<AuditLog> root, List<EntityRef> entities) {
        List<Predicate> alternatives = new ArrayList<>();
        for (EntityRef entity : entities) {
            List<Long> ids = List.copyOf(entity.ids());
            for (int start = 0; start < ids.size(); start += IN_CHUNK_SIZE) {
                List<Long> chunk = ids.subList(start, Math.min(start + IN_CHUNK_SIZE, ids.size()));
                alternatives.add(builder.and(
                        builder.equal(root.get("entidadeTipo"), entity.type()),
                        root.get("entidadeId").in(chunk)));
            }
        }
        if (alternatives.isEmpty()) {
            return builder.disjunction();
        }
        return builder.or(alternatives.toArray(new Predicate[0]));
    }

}
