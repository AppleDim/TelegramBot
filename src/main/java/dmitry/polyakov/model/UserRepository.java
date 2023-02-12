package dmitry.polyakov.model;

import org.springframework.data.repository.CrudRepository;

/**
 * @author Dmitry Polyakov
 * @created 12.01.2023 22:45
 */
public interface UserRepository extends CrudRepository<User, Long> {
}
