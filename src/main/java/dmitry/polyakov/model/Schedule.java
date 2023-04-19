package dmitry.polyakov.model;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;

/**
 * @author Dmitry Polyakov
 * @created 12.01.2023 22:01
 */

@Getter
@Setter
@Entity (name ="scheduleTable")
public class Schedule {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    private String message;
}
