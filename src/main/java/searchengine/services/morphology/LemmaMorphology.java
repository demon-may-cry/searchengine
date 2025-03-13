package searchengine.services.morphology;

import java.util.HashMap;
import java.util.List;

public interface LemmaMorphology {
    HashMap<String, Integer> collectLemmas(String content);

    List<String> getLemma(String lemma);
}
