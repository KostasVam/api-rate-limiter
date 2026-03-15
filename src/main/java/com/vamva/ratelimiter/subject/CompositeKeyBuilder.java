package com.vamva.ratelimiter.subject;

import com.vamva.ratelimiter.model.Policy;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Builds composite rate limit subject keys from multiple {@link SubjectExtractor}s.
 *
 * <p>Given a policy with subjects {@code ["user", "route"]}, this builder produces
 * a key like {@code user:123|route:POST:/api/payments} by invoking the corresponding
 * extractors and joining their results with {@code |}.</p>
 *
 * <p>Returns {@link Optional#empty()} if any required extractor is missing or
 * cannot determine the subject value from the request.</p>
 */
@Slf4j
public class CompositeKeyBuilder {

    private final Map<String, SubjectExtractor> extractors;

    public CompositeKeyBuilder(List<SubjectExtractor> extractorList) {
        this.extractors = extractorList.stream()
                .collect(Collectors.toMap(SubjectExtractor::type, Function.identity()));
    }

    /**
     * Builds a composite subject key for the given policy and request.
     *
     * @param policy  the policy whose subject scopes define which extractors to use
     * @param request the incoming HTTP request
     * @return the composite key, or empty if any subject could not be extracted
     */
    public Optional<String> buildKey(Policy policy, HttpServletRequest request) {
        List<String> subjects = policy.getSubjects();
        if (subjects == null || subjects.isEmpty()) {
            return Optional.empty();
        }

        StringBuilder key = new StringBuilder();

        for (String subjectType : subjects) {
            SubjectExtractor extractor = extractors.get(subjectType);
            if (extractor == null) {
                log.warn("No extractor registered for subject type '{}' in policy '{}'",
                        subjectType, policy.getId());
                return Optional.empty();
            }

            Optional<String> value = extractor.extract(request);
            if (value.isEmpty()) {
                return Optional.empty();
            }

            if (key.length() > 0) {
                key.append("|");
            }
            key.append(subjectType).append(":").append(value.get());
        }

        return key.length() > 0 ? Optional.of(key.toString()) : Optional.empty();
    }
}
