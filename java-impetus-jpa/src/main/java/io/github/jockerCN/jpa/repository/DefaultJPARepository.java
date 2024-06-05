package io.github.jockerCN.jpa.repository;



import io.github.jockerCN.jpa.pojo.JpaPojo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
@NoRepositoryBean
public interface DefaultJPARepository<T extends JpaPojo, ID> extends JpaRepository<T, ID> {
}
