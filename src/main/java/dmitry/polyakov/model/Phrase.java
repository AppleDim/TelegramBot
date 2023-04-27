package dmitry.polyakov.model;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;

@Entity
@Table(name = "phrase_table")
@Getter
@Setter
public class Phrase {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @Column(nullable = false)
    private String phrase;

    @Column(nullable = false)
    private int count;

    public Phrase() {
    }

    public Phrase(String phrase, int count) {
        this.phrase = phrase;
        this.count = count;
    }
}
