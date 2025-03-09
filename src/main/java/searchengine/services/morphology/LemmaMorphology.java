package searchengine.services.morphology;

import searchengine.model.PageEntity;

import java.util.HashMap;
import java.util.List;

public interface LemmaMorphology {
    HashMap<String, Integer> collectLemmas(String content, PageEntity page);

    List<String> getLemma(String lemma, PageEntity page);
}
