package searchengine.services.morphology;

import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

@Slf4j
@Component
public class LemmaMorphologyImpl implements LemmaMorphology {
    private static final String REGEX = "(?<NotCyrillic>[\\p{Punct}\\p{Co}\\p{ASCII}\\p{S}\\p{Lo}’‘№₽©◄«»—|@–…“”„ℹ]+)";
    private static RussianLuceneMorphology russianLuceneMorphology;

    static {
        try {
            russianLuceneMorphology = new RussianLuceneMorphology();
        } catch (IOException ex) {
            log.error(ex.getMessage());
        }
    }

    /**
     * Возвращает коллекцию Map лемм и их количество
     *
     * @param content контент страницы (HTML-код)
     * @param page    страница сайта на котором произошло исключение
     * @return HashMap<>
     */
    @Override
    public HashMap<String, Integer> collectLemmas(String content) {
        content = content.toLowerCase(Locale.ROOT).replaceAll(REGEX, " ");
        HashMap<String, Integer> lemmasMap = new HashMap<>();
        String[] lemmas = content.toLowerCase(Locale.ROOT).split("\\s+");
        for (var l : lemmas) {
            List<String> words = getLemma(l);
            for (var s : words) {
                lemmasMap.put(s, lemmasMap.getOrDefault(s, 0) + 1);
            }
        }
        return lemmasMap;
    }

    /**
     * Возвращает список лемм
     *
     * @param lemma лемма
     * @param page  страница сайта на котором произошло исключение
     * @return List<>
     */
    @Override
    public List<String> getLemma(String lemma) {
        List<String> lemmaList = new ArrayList<>();
        try {
            if (!lemma.isEmpty()) {
                List<String> rusForm = russianLuceneMorphology.getNormalForms(lemma);
                if (!isServiceWord(lemma)) {
                    lemmaList.addAll(rusForm);
                }
            }
        } catch (Exception ex) {
            log.error("{}", ex.getMessage()); //TODO: Symbol ️ is not small cirillic letter in page 2
        }
        return lemmaList;
    }

    /**
     * Проверка служебной части речи
     *
     * @param lemma лемма
     * @return boolean
     */
    private boolean isServiceWord(String lemma) {
        List<String> morphForm = russianLuceneMorphology.getMorphInfo(lemma);
        for (var s : morphForm) {
            if (s.contains("ПРЕДЛ")
                    || s.contains("СОЮЗ")
                    || s.contains("МЕЖД")
                    || s.contains("МС")
                    || s.contains("ЧАСТ")
                    || s.length() <= 3) {
                return true;
            }
        }
        return false;
    }
}
