package com.clouddeploy.service.matching;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class JobDescriptionParser {

    private static final Logger log = LoggerFactory.getLogger(JobDescriptionParser.class);

    private final SkillNormalizationService normalizationService;

    public record ParsedJobSkill(String canonicalKey, String displayName, String category, boolean required, String matchedText) {}

    public JobDescriptionParser(SkillNormalizationService normalizationService) {
        this.normalizationService = normalizationService;
    }

    /**
     * Parses title and description, segmenting into required vs preferred qualifications
     * and identifying controlled skills.
     */
    public List<ParsedJobSkill> parseJobSkills(String title, String description) {
        String fullText = ((title != null ? title : "") + "\n\n" + (description != null ? description : "")).trim();
        if (fullText.isEmpty()) {
            return Collections.emptyList();
        }

        // Segment text into sections
        SectionSplit split = splitSections(fullText);

        Map<String, ParsedJobSkill> detectedSkills = new LinkedHashMap<>();

        // 1. Scan title for required core skills
        if (title != null && !title.trim().isEmpty()) {
            scanTextForSkills(title, true, detectedSkills);
        }

        // 2. Scan required section
        scanTextForSkills(split.requiredText(), true, detectedSkills);

        // 3. Scan preferred section
        scanTextForSkills(split.preferredText(), false, detectedSkills);

        // 4. Scan unclassified / remaining text as required
        scanTextForSkills(split.unclassifiedText(), true, detectedSkills);

        return new ArrayList<>(detectedSkills.values());
    }

    private record SectionSplit(String requiredText, String preferredText, String unclassifiedText) {}

    private SectionSplit splitSections(String text) {
        // Look for common headers
        Pattern requiredHeader = Pattern.compile("(?im)^(?:#{1,6}\\s*)?(?:minimum|basic|required|mandatory)\\s+(?:qualifications?|requirements?|skills?|experience)[:\\s]*$|(?im)^(?:requirements?|must have)[:\\s]*$");
        Pattern preferredHeader = Pattern.compile("(?im)^(?:#{1,6}\\s*)?(?:preferred|desired|nice to have|bonus|plus)\\s+(?:qualifications?|requirements?|skills?|experience)[:\\s]*$|(?im)^(?:preferred|nice to have)[:\\s]*$");

        Matcher reqMatcher = requiredHeader.matcher(text);
        Matcher prefMatcher = preferredHeader.matcher(text);

        int reqStart = reqMatcher.find() ? reqMatcher.start() : -1;
        int prefStart = prefMatcher.find() ? prefMatcher.start() : -1;

        if (reqStart == -1 && prefStart == -1) {
            // No explicit headers found: entire description is treated as primary requirements
            return new SectionSplit("", "", text);
        }

        String requiredPart = "";
        String preferredPart = "";
        String unclassifiedPart = "";

        if (reqStart != -1 && prefStart != -1) {
            if (reqStart < prefStart) {
                unclassifiedPart = text.substring(0, reqStart);
                requiredPart = text.substring(reqStart, prefStart);
                preferredPart = text.substring(prefStart);
            } else {
                unclassifiedPart = text.substring(0, prefStart);
                preferredPart = text.substring(prefStart, reqStart);
                requiredPart = text.substring(reqStart);
            }
        } else if (reqStart != -1) {
            unclassifiedPart = text.substring(0, reqStart);
            requiredPart = text.substring(reqStart);
        } else {
            unclassifiedPart = text.substring(0, prefStart);
            preferredPart = text.substring(prefStart);
        }

        return new SectionSplit(requiredPart, preferredPart, unclassifiedPart);
    }

    private void scanTextForSkills(String text, boolean required, Map<String, ParsedJobSkill> targetMap) {
        if (text == null || text.trim().isEmpty()) return;

        for (SkillNormalizationService.SkillDefinition def : normalizationService.getAllDefinitions()) {
            for (String alias : def.aliases()) {
                if (matchesSkillInText(text, alias, def.canonicalKey())) {
                    // If already detected as required, preserve required priority
                    if (targetMap.containsKey(def.canonicalKey())) {
                        ParsedJobSkill existing = targetMap.get(def.canonicalKey());
                        if (!existing.required() && required) {
                            targetMap.put(def.canonicalKey(), new ParsedJobSkill(def.canonicalKey(), def.displayName(), def.category(), true, alias));
                        }
                    } else {
                        targetMap.put(def.canonicalKey(), new ParsedJobSkill(def.canonicalKey(), def.displayName(), def.category(), required, alias));
                    }
                    break; // match first alias for this definition
                }
            }
        }
    }

    private boolean matchesSkillInText(String text, String alias, String canonicalKey) {
        String lowerText = text.toLowerCase();
        String lowerAlias = alias.toLowerCase();

        // Specific collision guard for Java: Java must not match JavaScript!
        if (canonicalKey.equals("java")) {
            Pattern p = Pattern.compile("(?i)(?:^|[^a-zA-Z0-9])" + Pattern.quote(alias) + "(?:$|[^a-zA-Z0-9])");
            Matcher m = p.matcher(text);
            while (m.find()) {
                int end = m.end();
                String remaining = text.substring(Math.min(end, text.length())).toLowerCase();
                if (!remaining.startsWith("script")) {
                    return true;
                }
            }
            return false;
        }

        // Specific collision guard for C: C must not match C++ or C# or arbitrary single letters
        if (canonicalKey.equals("c")) {
            Pattern p = Pattern.compile("(?i)(?:^|[^a-zA-Z0-9+#])c(?:$|[^a-zA-Z0-9+#])");
            Matcher m = p.matcher(text);
            return m.find();
        }

        // Exact word boundary regex
        Pattern pattern = Pattern.compile("(?i)(?:^|[^a-zA-Z0-9+#_])" + Pattern.quote(lowerAlias) + "(?:$|[^a-zA-Z0-9+#_])");
        return pattern.matcher(lowerText).find();
    }
}
