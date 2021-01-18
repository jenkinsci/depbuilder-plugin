package eu.royalsloth.depbuilder.dsl;

import eu.royalsloth.depbuilder.dsl.scheduling.BuildAgent;
import eu.royalsloth.depbuilder.dsl.scheduling.BuildAgentType;
import eu.royalsloth.depbuilder.dsl.scheduling.BuildSettings;
import eu.royalsloth.depbuilder.dsl.scheduling.SchedulerSettings;
import eu.royalsloth.depbuilder.dsl.utils.TimeUtils;
import eu.royalsloth.depbuilder.jenkins.PluginVersion;

import javax.annotation.CheckForNull;
import java.time.Duration;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

public class DslParser {
    public static final String ALL_SETTINGS = "_ALL";
    public static final String SCHEDULER_SETTINGS = "_BUILD";
    private static final Set<String> EMPTY_HASH = new HashSet<>();

    public static ParsedBuild parseBuildNoVerify(String input) throws ParseException {
        // we don't care about existence of agents, build nodes, etc... we only care about parsing the text
        SettingsVerifier verifier = new SettingsVerifier();
        verifier.setVerify(false);
        return parseBuild(input, verifier);
    }

    public static ParsedBuild parseBuild(String input,
            SettingsVerifier settingsVerifier) throws ParseException {
        DslLexer.Tokenizer tokenizer = new DslLexer.Tokenizer(input);
        Map<String, Set<String>> nodeChildrenMapping = new HashMap<>();
        BuildSettingsContainer buildSettings = new BuildSettingsContainer();
        SchedulerSettings schedulerSettings = new SchedulerSettings();

        Token token = tokenizer.getNextToken();
        boolean parse = true;
        while (parse) {
            switch (token.type) {
                case EOF:
                    parse = false;
                    break;
                case SEMICOLON:
                    token = tokenizer.getNextToken();
                    break;
                case UNKNOWN:
                case ERROR:
                    throw ParseException.create(tokenizer, token,
                                                String.format("Error while parsing tokens %s", token.text));
                case IDENTIFIER:
                case STRING: {
                    // every identifier has to be checked against the known identifiers
                    Set<String> nodeChildren = verifyIdentifier(settingsVerifier, tokenizer, nodeChildrenMapping, token);
                    Token nextToken = tokenizer.getNextToken();
                    if (nextToken.type == TokenType.EOF) {
                        parse = false;
                        break;
                    }

                    // parse build node settings (works for _ALL identifier as well)
                    if (nextToken.type == TokenType.LEFT_BRACE) {
                        final String buildNode = token.text;
                        if (SCHEDULER_SETTINGS.equals(buildNode)) {
                            parseSchedulerSettings(tokenizer, token.text, schedulerSettings);
                            continue;
                        }

                        BuildSettings settings = buildSettings.getOrCreate(buildNode);
                        BuildSettings updatedSettings = parseBuildNodeSettings(tokenizer, settings, settingsVerifier);
                        // this put is not strictly necessary, because we are modifying the settings node
                        buildSettings.put(buildNode, updatedSettings);
                        token = tokenizer.getNextToken();
                        continue;
                    }

                    // if next token is comma that means we are parsing multiple build
                    // node settings: A,B {...}
                    if (nextToken.type == TokenType.COMMA) {
                        List<String> settingIdentifiers = new ArrayList<>();
                        settingIdentifiers.add(token.text);
                        while (true) {
                            nextToken = tokenizer.getNextToken();
                            if (nextToken.type == TokenType.COMMA) {
                                continue;
                            }

                            if (nextToken.isIdentifier()) {
                                verifyIdentifier(settingsVerifier, tokenizer, nodeChildrenMapping, nextToken);
                                settingIdentifiers.add(nextToken.text);
                                continue;
                            }

                            if (nextToken.type == TokenType.LEFT_BRACE) {
                                // tokenizer reparses the settings field for every build node
                                // in front of the settings block (parsing should be very cheap)
                                // as otherwise we would have to write the logic to update only
                                // the fields that has changed:
                                // A { weight: 1, agent: [aa] }
                                // A, B { weight: 2 }
                                // In this case the weight should change but agent field for A should not.
                                // The more fields we have the more complex the update logic get
                                // so wasting some cycles with reparsing seems like the most sane decision
                                int initialTokenizerPos = tokenizer.position;
                                int lastTokenizerPos = -1;
                                for (String buildNode : settingIdentifiers) {
                                    BuildSettings settings = buildSettings.getOrCreate(buildNode);
                                    BuildSettings updatedBuildSettings = parseBuildNodeSettings(tokenizer, settings, settingsVerifier);
                                    buildSettings.put(buildNode, updatedBuildSettings);
                                    lastTokenizerPos = tokenizer.position;
                                    tokenizer.position = initialTokenizerPos;
                                }
                                // after done parsing multiple time, skip to the
                                // last position (after the A, B {} settings block)
                                tokenizer.position = lastTokenizerPos;
                                break; // break inner loop
                            }

                            // a supported element was not found
                            throw ParseException.create(tokenizer, nextToken, String.format("expected build node identifier or '{', got '%s'", nextToken.text));
                        }

                        // finished with parsing multiple settings block
                        token = tokenizer.getNextToken();
                        continue;
                    }

                    // we are not parsing build node settings, check all other possible options
                    if (nextToken.type == TokenType.SEMICOLON) {
                        // we have a lone node or a new line, find a next token and repeat the loop
                        token = tokenizer.getNextToken();
                        continue;
                    }

                    // we have an identifier and an arrow (A -> ?), parse the immediate child
                    if (nextToken.type == TokenType.RIGHT_ARROW) {
                        nextToken = tokenizer.getNextToken();
                        if (nextToken.isIdentifier()) {
                            // we have a child, create a relation
                            verifyIdentifier(settingsVerifier, tokenizer, nodeChildrenMapping, nextToken);
                            nodeChildren.add(nextToken.text);
                            token = nextToken;
                            continue;
                        }

                        // A -> (not an identifier) which means we have a parse error
                        // Even if the EOF happened or semicolon appeared, that is still a parsing error
                        throw ParseException.create(tokenizer, nextToken,
                                                    String.format("expected build node identifier, got: '%s'",
                                                                  nextToken.text));
                    }

                    // Found a parsing problem, we should find an arrow but it wasn't there.
                    //
                    // For now an exception is thrown on the first error, because if we keep
                    // parsing that leads to the dark path of C++ error reporting - tons of
                    // errors when one semicolon is missing and nobody wants to see that.
                    throw ParseException.create(tokenizer, nextToken,
                                                String.format("expected '->', got: '%s'", nextToken.text));
                }
                default:
                    throw ParseException.create(tokenizer, token,
                                                String.format("expected build node identifier, got: '%s'", token.text));
            }
        }

        // last pass over all nodes, in case some nodes did not have any settings,
        // they should at least have the default settings (e.g global setting if it exists)
        List<ParsedBuildJob> parsedBuildJobs = new ArrayList<>(nodeChildrenMapping.size());
        for (Map.Entry<String, Set<String>> entry : nodeChildrenMapping.entrySet()) {
            final String buildNodeName = entry.getKey();
            final Set<String> buildNodeChildren = entry.getValue();
            BuildSettings settings = buildSettings.getOrCreate(buildNodeName);
            ParsedBuildJob node = new ParsedBuildJob(buildNodeName, buildNodeChildren, settings);
            parsedBuildJobs.add(node);
        }

        ParsedBuild parsedBuild = new ParsedBuild(schedulerSettings, parsedBuildJobs);
        return parsedBuild;
    }

    /**
     * Verify token identifier against the known identifiers. If the identifier does not exist, an exception
     * will be thrown
     *
     * @return known children (build jobs) of the verified identifier
     */
    private static Set<String> verifyIdentifier(SettingsVerifier settingsVerifier,
            DslLexer.Tokenizer tokenizer, Map<String, Set<String>> nodeChildrenMapping,
            Token token) throws ParseException {
        String buildNodeId = token.text;

        final boolean isSetting = ALL_SETTINGS.equals(buildNodeId)
                || SCHEDULER_SETTINGS.equals(buildNodeId);
        if (isSetting) {
            // this is kinda dumb and probably also slow. Build settings shouldn't be
            // a part of the node children mapping
            return EMPTY_HASH;
        }

        final boolean nodeDoesNotExist = !settingsVerifier.buildNodeExists(buildNodeId);
        if (nodeDoesNotExist) {
            // Workaround: if we have thousands of nodes to verify, the exception
            // message listing all the possible options could become very large
            // and therefore hard to read. Therefore we limit number of possible
            // options here. An ever better solution to this problem would be a
            // fuzzy string matcher that would suggest the possible mistyped words
            // https://mvnrepository.com/artifact/com.intuit.fuzzymatcher/fuzzy-matcher/1.0.4
            Set<String> buildJobs = settingsVerifier.getBuildNodes();
            int MAX_ELEMENTS = 10;
            int size = Math.min(buildJobs.size(), MAX_ELEMENTS);
            StringBuilder builder = new StringBuilder();
            int counter = 0;
            for (String buildJob : buildJobs) {
                if (counter == 0) {
                    builder.append(buildJob);
                } else {
                    builder.append(", ");
                    builder.append(buildJob);
                }

                counter++;
                if (counter > size) {
                    break;
                }
            }
            if (size == MAX_ELEMENTS) {
                builder.append(", etc...");
            }
            throw ParseException.create(tokenizer, token,
                                        String.format("build node '%s' does not exist, possible options [%s]",
                                                      buildNodeId, builder.toString()));
        }
        return nodeChildrenMapping.computeIfAbsent(buildNodeId, s -> new HashSet<>());
    }


    /**
     * Parsing settings for the entire build (scheduler)
     * <p>
     * _BUILD { maxDuration: "00:15" throttle: "15:00|10" }
     */
    private static SchedulerSettings parseSchedulerSettings(DslLexer.Tokenizer tokenizer,
            String sectionIdentifier, SchedulerSettings settings) throws ParseException {
        Token token;
        while (true) {
            token = tokenizer.getNextToken();
            token = eatUpSemicolons(tokenizer, token);
            if (token.type == TokenType.RIGHT_BRACE) {
                break;
            }
            if (token.type == TokenType.EOF) {
                throw ParseException.create(tokenizer, token, String.format("'%s' settings are missing a closing brace '}'",
                                                                            sectionIdentifier));
            }

            // expecting => 'something': 'xxx'
            if (!token.isIdentifier()) {
                throw ParseException.create(tokenizer, token, String.format("expected a new settings field identifier, got: '%s'",
                                                                            token.text));
            }

            String settingIdentifier = token.text;
            expectAndParseColon(tokenizer, sectionIdentifier, settingIdentifier);
            switch (settingIdentifier) {
                case "maxDuration":
                    Duration maxDuration = parseDuration(tokenizer, settingIdentifier);
                    if (maxDuration.toMillis() == BuildSettings.INFINITE_DURATION.toMillis()) {
                        // we don't want infinite time for the entire build just in case
                        // something goes wrong and we don't want to loop forever.
                        // Therefore we should set the default scheduler settings max build time
                        maxDuration = SchedulerSettings.DEFAULT_MAX_BUILD_TIME;
                    }
                    settings.maxDuration = maxDuration;
                    break;
                case "buildThrottle":
                    if (PluginVersion.isCommunity()) {
                        // parse the array, even if we don't care about the contents
                        // this makes sure that we don't break the configuration if the
                        // user decides to stop using enterprise version and falls back to
                        // community edition. We still have to parse an entire field,
                        // since there may be additional setting field after this one.
                        String arrayField = chompArray(tokenizer, token);
                    } else {
                        // @PRO:
                        String arrayField = chompArray(tokenizer, token);
                        // ProParser.parseThrottle(tokenizer, settings, settingIdentifier);
                    }
                    break;
                default:
                    if (PluginVersion.isCommunity()) {
                        throw ParseException.create(tokenizer, token, String.format("unknown setting field '%s', supported settings: [maxDuration]", token.text));
                    } else {
                        throw ParseException.create(tokenizer, token, String.format("unknown setting field '%s', supported settings: [maxDuration, buildThrottle]", token.text));
                    }
            }
        }
        return settings;
    }

    // chomp array for which we don't care what it contains
    public static String chompArray(DslLexer.Tokenizer tokenizer, Token token) throws ParseException {
        int start = tokenizer.position;
        int end = start;
        Token t = tokenizer.getNextToken();
        t = eatUpSemicolons(tokenizer, t);
        if (t.type != TokenType.LEFT_BRACKET) {
            throw ParseException.create(tokenizer, t, String.format("builtThrottle value expected '[', got '%s'", t.text));
        }
        while (true) {
            t = tokenizer.getNextToken();
            if (t.type == TokenType.EOF || t.type == TokenType.RIGHT_BRACE) {
                throw ParseException.create(tokenizer, t, String.format("buildThrottle field expected identifier or ']', but got %s. Did you forget the closing ']'?", token.text));
            }

            if (t.type == TokenType.RIGHT_BRACKET) {
                end = tokenizer.position;
                break;
            }
        }
        return tokenizer.substring(start, end);
    }

    private static BuildSettings parseBuildNodeSettings(DslLexer.Tokenizer tokenizer,
            BuildSettings buildNodeSettings,
            SettingsVerifier settingsVerifier) throws ParseException {
        while (true) {
            Token token = tokenizer.getNextToken();
            token = eatUpSemicolons(tokenizer, token);

            if (token.type == TokenType.RIGHT_BRACE) {
                break;
            }

            if (token.type == TokenType.EOF) {
                throw ParseException.create(tokenizer, token,
                                            String.format("'%s' settings are missing a closing brace '}'",
                                                          buildNodeSettings.getName()));
            }

            // parsing identifier: assignment
            // such as => A: 123 || A: B || A: "blah whatever"
            if (!token.isIdentifier()) {
                throw ParseException.create(tokenizer, token, String.format("expected new settings field identifier, got: '%s'",
                                                                            token.text));
            }

            // first settings identifier found
            final Token settingsIdentifier = token;
            // check the settings field and parse it accordingly to the expected format
            //
            // each field contains expectAndParseColon method call, because we would like
            //to first validate the settings field before we check if the colon exists.
            // Otherwise if somebody forgets to close the settings block, it would throw
            // a weird colon is missing error message which is really confusing, e.g:
            // A { ...
            // myProject
            final String settingsId = buildNodeSettings.getName();
            final String settingsField = settingsIdentifier.text;
            switch (settingsField) {
                case "agent":
                    token = expectAndParseColon(tokenizer, settingsId, settingsField);
                    List<String> parsedAgents = parseStringArray(tokenizer);

                    // we want unique agents in inserted order
                    Set<String> unknownAgents = new LinkedHashSet<>();
                    Set<String> validAgents = new LinkedHashSet<>();
                    // FUTURE: support any:windows, any:linux agent options
                    for (String agent : parsedAgents) {
                        if (settingsVerifier.agentExists(agent)) {
                            validAgents.add(agent);
                        } else {
                            unknownAgents.add(agent);
                        }
                    }

                    if (validAgents.isEmpty()) {
                        // no valid agent was found in the settings verifier
                        throw ParseException.create(tokenizer, token,
                                                    String.format("no valid agent found, expected: %s, found: %s",
                                                                  settingsVerifier.getKnownAgents(), parsedAgents));
                    }

                    // we found at least one valid agent
                    // check if there are any invalid agents so we can trigger a warning
                    final boolean invalidAgentsExist = !unknownAgents.isEmpty();
                    if (invalidAgentsExist) {
                        // an exception is not thrown, as the build nodes could potentially go up or down
                        // at any time and we should still allow building with at least one valid build agent
                        System.out.println(String.format("Found %d unknown build agents: %s", unknownAgents.size(),
                                                         unknownAgents));
                    }

                    List<BuildAgent> agents = validAgents.stream()
                                                         .map(agent -> new BuildAgent(agent, BuildAgentType.ANY))
                                                         .collect(Collectors.toList());
                    buildNodeSettings.setAgents(agents);
                    break;
                case "onParentFailure":
                    token = expectAndParseColon(tokenizer, settingsId, settingsField);
                    token = tokenizer.getNextToken();
                    if (!token.isIdentifier()) {
                        throw ParseException.create(tokenizer, token,
                                                    String.format("expected modes: '%s', got: '%s'",
                                                                  BuildSettings.ParentFailureMode
                                                                          .allModes(), token.text));
                    }

                    Optional<BuildSettings.ParentFailureMode> mode = BuildSettings.ParentFailureMode.parse(token.text);
                    final boolean isEmpty = !mode.isPresent();
                    if (isEmpty) {
                        // mode was not found, or the input was empty, throw an exception
                        throw ParseException.create(tokenizer, token, String.format(
                                "unknown onParentFailure mode for build node %s. Expected modes: %s, got: '%s'",
                                buildNodeSettings.getName(), BuildSettings.ParentFailureMode.allModes(), token.text));
                    }
                    buildNodeSettings.setOnParentFailure(mode.get());
                    break;
                case "maxDuration":
                    token = expectAndParseColon(tokenizer, settingsId, settingsField);
                    Duration duration = parseDuration(tokenizer, settingsIdentifier.text);
                    buildNodeSettings.setMaxDuration(duration);
                    break;
                case "weight": {
                    token = expectAndParseColon(tokenizer, settingsId, settingsField);
                    token = tokenizer.getNextToken();
                    if (!token.isNumber()) {
                        throw ParseException.create(tokenizer, token,
                                                    String.format("invalid weight value expected number, got: '%s'",
                                                                  token.text));
                    }

                    try {
                        int weight = Integer.parseInt(token.text);
                        if (weight < 0) {
                            throw ParseException.create(tokenizer, token,
                                                        String.format("node %s, expected weight > 0, got: %d",
                                                                      buildNodeSettings.getName(), weight));
                        }
                        buildNodeSettings.setWeight(weight);
                    } catch (NumberFormatException e) {
                        throw ParseException.create(tokenizer, token,
                                                    String.format("invalid weight value expected integer, got: '%s'",
                                                                  token.text));
                    }
                }
                break;
                default:
                    final String settingField = settingsIdentifier.text;

                    // in strict mode unknown settings are forbidden. In production we
                    // would like to strictly check settings most of the time, but I am
                    // still leaving the old code of parsing the unknown settings in,
                    // in case we might need it at some point
                    // (e.g: allowing deprecated settings might be useful)
                    if (settingsVerifier.getStrictMode()) {
                        throw ParseException.create(tokenizer, token, String.format("unknown setting %s, supported settings: [agent, maxDuration, onParentFailure, weight]", settingField));
                    }

                    token = expectAndParseColon(tokenizer, settingsId, settingsField);
                    token = tokenizer.peekNextToken();
                    final boolean foundArray = token.type == TokenType.LEFT_BRACKET;
                    if (foundArray) {
                        List<String> settingValue = parseArray(tokenizer, String.class);
                        BuildSettings.UnknownSetting setting = new BuildSettings.UnknownSetting(settingField,
                                                                                                settingValue,
                                                                                                tokenizer.line);
                        buildNodeSettings.addUnknownSetting(setting);
                        continue;
                    }

                    // we peeked the token before, it was not array now we can actually get it
                    token = tokenizer.getNextToken();
                    final boolean foundIdentifierValue = token.isIdentifier() || token.isNumber();
                    if (foundIdentifierValue) {
                        Object settingValue = token.text;
                        BuildSettings.UnknownSetting setting = new BuildSettings.UnknownSetting(settingField,
                                                                                                settingValue,
                                                                                                tokenizer.line);
                        buildNodeSettings.addUnknownSetting(setting);
                    } else {
                        // we did not found an identifier value, throw a parsing exception
                        throw ParseException.create(tokenizer, token,
                                                    String.format("expected %s field value, got: '%s'",
                                                                  settingsIdentifier.text, token.text));
                    }
            }
        }

        return buildNodeSettings;
    }

    /**
     * Parse time in "hh:mm" or hh:mm (without quotes) format or else throw an exception
     */
    public static LocalTime parseTime(DslLexer.Tokenizer tokenizer,
            String identifier, Token alreadyParsedToken) throws ParseException {
        if (alreadyParsedToken.type == TokenType.STRING) {
            String[] arr = alreadyParsedToken.text.split(":");
            if (arr.length != 2) {
                throw ParseException.create(tokenizer, alreadyParsedToken, String.format("%s value expected hh:mm, got '%s'", identifier, alreadyParsedToken.text));
            }

            try {
                String hours = arr[0];
                String minutes = arr[1];
                return TimeUtils.parseTime(hours, minutes);
            } catch (ParseException e) {
                throw ParseException.create(tokenizer, alreadyParsedToken, e.getMessage());
            }
        }

        // build time could be inserted without the quotations (hh:mm)
        final Token firstToken = alreadyParsedToken;
        if (!firstToken.isNumber()) {
            throw ParseException.create(tokenizer, alreadyParsedToken, String.format(
                    "%s value expected hh:mm, got: '%s'", identifier, firstToken.text));
        }

        Token secondToken = tokenizer.getNextToken();
        if (secondToken.type != TokenType.COLON) {
            throw ParseException.create(tokenizer, secondToken, String.format(
                    "%s value expected hh:mm, got: '%s:%s'", identifier, firstToken.text,
                    secondToken.text));
        }

        // current parsed state is 'hh:'
        Token thirdToken = tokenizer.getNextToken();
        if (!thirdToken.isNumber()) {
            throw ParseException.create(tokenizer, thirdToken, String.format(
                    "%s value expected hh:mm, got: '%s:%s'", identifier, firstToken.text,
                    thirdToken.text));
        }

        try {
            String hours = firstToken.text;
            String minutes = thirdToken.text;
            return TimeUtils.parseTime(hours, minutes);
        } catch (Exception e) {
            throw ParseException.create(tokenizer, thirdToken, String.format("invalid %s value, %s", identifier,
                                                                             e.getMessage()));
        }
    }

    public static Duration parseDuration(DslLexer.Tokenizer tokenizer,
            String identifier) throws ParseException {
        Token token = tokenizer.getNextToken();
        return parseDuration(tokenizer, identifier, token);
    }

    public static Duration parseDuration(DslLexer.Tokenizer tokenizer, String identifier,
            Token alreadyParsedToken) throws ParseException {
        // this code is very similar to the one for parsing time, but let's not bother
        // refactoring for the sake of refactoring because it will make it way more
        // convoluted.
        if (alreadyParsedToken.type == TokenType.STRING) {
            try {
                Duration duration = TimeUtils.parseDuration(alreadyParsedToken.text);
                return duration;
            } catch (ParseException e) {
                throw ParseException.create(tokenizer, alreadyParsedToken, e.getMessage());
            }
        }

        // build time could be inserted without the quotations (mm, hh:mm)
        final Token hoursPart = alreadyParsedToken;
        if (!hoursPart.isNumber()) {
            throw ParseException.create(tokenizer, alreadyParsedToken, String.format(
                    "invalid %s value, expected mm or hh:mm, got: '%s'", identifier, hoursPart.text));
        }

        Token firstColon = tokenizer.getNextToken();
        if (firstColon.type == TokenType.SEMICOLON || firstColon.type == TokenType.EOF) {
            try {
                // we parsed build time that consists of minutes only
                Duration duration = TimeUtils.parseDuration(hoursPart.text);
                return duration;
            } catch (ParseException e) {
                throw ParseException.create(tokenizer, alreadyParsedToken,
                                            String.format("invalid %s value, %s", identifier, e.getMessage()));
            }
        }

        // we are parsing hh:mm format
        if (firstColon.type != TokenType.COLON) {
            throw ParseException.create(tokenizer, firstColon, String.format(
                    "invalid %s value expected mm or hh:mm, got: '%s%s'", identifier, hoursPart.text,
                    firstColon.text));
        }

        // current parsed state is 'hh:'
        Token minutesPart = tokenizer.getNextToken();
        if (!minutesPart.isNumber()) {
            throw ParseException.create(tokenizer, minutesPart, String.format(
                    "invalid %s value expected mm or hh:mm, got: '%s:%s'", identifier, hoursPart.text,
                    minutesPart.text));
        }

        // right now this info that we support seconds is not exported to the user
        // because we mostly don't want to support them since this part was added to
        // support test cases
        Token possibleColon = tokenizer.peekNextToken();
        if (possibleColon.type == TokenType.COLON) {
            // if that is the case, the user has already provided a seconds part of the duration
            Token colon = tokenizer.getNextToken();
            Token secondsPart = tokenizer.getNextToken();
            if (!secondsPart.isNumber()) {
                throw ParseException.create(tokenizer, secondsPart, String.format("invalid %s value expected hh:mm:ss, got '%s:%s:%s'", identifier, hoursPart.text, minutesPart.text, secondsPart.text));
            }

            try {
                return validateDuration(hoursPart.text, minutesPart.text, secondsPart.text);
            } catch (ParseException e) {
                throw ParseException.create(tokenizer, secondsPart, String.format("invalid %s value, %s", identifier, e
                        .getMessage()));
            }
        }

        try {
            String hours = hoursPart.text;
            String minutes = minutesPart.text;
            return validateDuration(hours, minutes, "");
        } catch (Exception e) {
            throw ParseException.create(tokenizer, minutesPart, String.format("invalid %s value, %s", identifier,
                                                                              e.getMessage()));
        }
    }

    private static Duration validateDuration(String hoursPart, String minutesPart, String secondsPart) throws ParseException {
        if (secondsPart.isEmpty()) {
            try {
                long hours = Integer.parseInt(hoursPart);
                long minutes = Integer.parseInt(minutesPart);
                return validateDuration(hours, minutes, 0);
            } catch (NumberFormatException e) {
                throw new ParseException(String.format("%s:%s is not a valid duration", hoursPart, minutesPart));
            }
        }

        try {
            long hours = Integer.parseInt(hoursPart);
            long minutes = Integer.parseInt(minutesPart);
            long seconds = Integer.parseInt(secondsPart);
            return validateDuration(hours, minutes, seconds);
        } catch (NumberFormatException e) {
            throw new ParseException(String.format("'%s:%s:%s' is not a valid duration", hoursPart, minutesPart, secondsPart));
        }
    }

    private static Duration validateDuration(long hours, long minutes, long seconds) throws ParseException {
        if (hours < 0) {
            throw new ParseException(String.format("'%d' is not a valid hours part, expected hours should be > 0", hours));
        }

        if (minutes < 0 || minutes > 59) {
            throw new ParseException(String.format("'%d' is not a valid minutes part, expected minutes range: [0, 59]", minutes));
        }

        if (seconds < 0 || seconds > 59) {
            throw new ParseException(String.format("'%d' is not a valid seconds part, expected seconds range: [0, 59]", seconds));
        }

        Duration duration = Duration.ofHours(hours).plusMinutes(minutes).plusSeconds(seconds);
        return duration;
    }

    public static List<String> parseStringArray(DslLexer.Tokenizer tokenizer) throws ParseException {
        return parseArray(tokenizer, String.class);
    }

    public static List<Integer> parseIntArray(DslLexer.Tokenizer tokenizer) throws ParseException {
        return parseArray(tokenizer, Integer.class);
    }

    public static <T> List<T> parseArray(DslLexer.Tokenizer tokenizer,
            Class<T> clazz) throws ParseException {
        Token token = tokenizer.getNextToken();
        if (token.type != TokenType.LEFT_BRACKET) {
            throw ParseException.create(tokenizer, token, String.format("Expected '[', got: '%s'", token.text));
        }

        List<T> array = new ArrayList<>();
        token = tokenizer.getNextToken();
        while (token.type != TokenType.RIGHT_BRACKET) { // parse array
            if (token.type == TokenType.EOF) {
                throw ParseException.create(tokenizer, token, String.format("expected '}', but found EOF"));
            }

            // token should be either identifier or a number (but not both, we should make array
            // of the same type)
            if (token.isIdentifier() || token.isNumber()) {
                // stupid java generics
                if (clazz == Integer.class) {
                    try {
                        Integer value = Integer.parseInt(token.text);
                        array.add((T) value);
                    } catch (Exception e) {
                        throw ParseException.create(tokenizer, token,
                                                    String.format("expected a number, got '%s'", token.text));
                    }
                } else if (clazz == Float.class) {
                    try {
                        Float value = Float.parseFloat(token.text);
                        array.add((T) value);
                    } catch (Exception e) {
                        throw ParseException.create(tokenizer, token,
                                                    String.format("expected a float, got '%s'", token.text));
                    }
                } else if (clazz == Double.class) {
                    try {
                        Double value = Double.parseDouble(token.text);
                        array.add((T) value);
                    } catch (Exception e) {
                        throw ParseException.create(tokenizer, token,
                                                    String.format("expected a double, got '%s'", token.text));
                    }
                } else if (clazz == String.class) {
                    array.add((T) token.text);
                } else if (clazz == Boolean.class) {
                    try {
                        Boolean b = Boolean.parseBoolean(token.text);
                        array.add((T) b);
                    } catch (Exception e) {
                        throw ParseException.create(tokenizer, token,
                                                    String.format("expected true/false, got: '%s'", token.text));
                    }
                } else {
                    // FUTURE: at this point we could provide an extractor that would extract the
                    // token text into appropriate format
                    //
                    // this is an illegal state, we don't know how to parse this type
                    throw new IllegalStateException(String.format("Can't parse %s type", clazz));
                }

                token = tokenizer.getNextToken();
                continue;
            }

            if (token.type == TokenType.COMMA) {
                token = tokenizer.getNextToken();
                continue;
            }

            if (token.type == TokenType.SEMICOLON) {
                // new line
                token = tokenizer.getNextToken();
                continue;
            }

            throw ParseException.create(tokenizer, token,
                                        String.format("expected array value or ']', got: '%s'", token.text));
        }
        return array;
    }


    /////////////////
    // Utilities
    ////////////////
    public static Token expectAndParseColon(DslLexer.Tokenizer tokenizer) throws ParseException {
        Token token = tokenizer.getNextToken();
        if (token.type != TokenType.COLON) {
            throw ParseException.create(tokenizer, token, String.format("expected ':', got '%s'", token.text));
        }
        return token;
    }

    public static Token expectAndParseColon(DslLexer.Tokenizer tokenizer, String settingsId, String settingsField) throws ParseException {
        Token token = tokenizer.getNextToken();
        if (token.type != TokenType.COLON) {
            throw ParseException.create(tokenizer, token, String.format("%s '%s' field is missing a value, expected ':', got '%s'", settingsId, settingsField, token.text));
        }
        return token;
    }

    public static Token eatUpSemicolons(DslLexer.Tokenizer tokenizer, Token token) {
        while (token.type == TokenType.SEMICOLON) {
            token = tokenizer.getNextToken();
        }
        return token;
    }

    public static int safeIntParse(DslLexer.Tokenizer tokenizer, Token token) throws ParseException {
        try {
            int i = Integer.parseInt(token.text);
            return i;
        } catch (Exception e) {
            throw ParseException.create(tokenizer, token, String.format("provided input '%s' is not an integer", token.text));
        }
    }

    /**
     * Special settings container for storing build settings of the build nodes
     */
    private static class BuildSettingsContainer {
        public Map<String, BuildSettings> buildSettings = new HashMap<>();
        @CheckForNull
        private BuildSettings allSettings;

        public boolean isGlobalSetting(String buildNodeName) {
            if (ALL_SETTINGS.equals(buildNodeName)) {
                return true;
            }
            return false;
        }

        public void setGlobalSetting(BuildSettings buildSetting) {
            // Right now we allow redefining _ALL blocks at different positions in
            // the DSL script (because our parsers allows us to do that).
            //
            // This is quite powerful but it could be also abused:
            // _ALL { xxx }
            // B { inherits xxx from _ALL}
            // _ALL { yyy }
            // C { inherits from both _ALLs (xxx and yyy) }
            //
            // B does not inherit yyy
            //
            // If we want to prevent this behaviour, create a globalSettings variable
            // and only put new settings block if the variable is null (this would
            // ensure that we update the _ALL settings only for the first time)
            this.allSettings = buildSetting;
        }

        @CheckForNull
        public BuildSettings getGlobalSettings() {
            return this.allSettings;
        }

        BuildSettings getOrCreate(String project) {
            BuildSettings buildSetting = this.buildSettings.get(project);
            if (buildSetting != null) {
                return buildSetting;
            }

            // build setting does not exist, create it
            BuildSettings globalSettings = getGlobalSettings();
            final boolean globalSettingExists = globalSettings != null;
            if (globalSettingExists) {
                // global settings are present, for this node make a copy of current global settings
                return this.buildSettings.computeIfAbsent(project, k -> BuildSettings.copy(globalSettings));
            }
            // global setting was not present, return a new default empty build setting
            return this.buildSettings.computeIfAbsent(project, k -> new BuildSettings(project));
        }

        public void put(String buildNode, BuildSettings updatedSettings) {
            if (isGlobalSetting(buildNode)) {
                setGlobalSetting(updatedSettings);
                return;
            }
            // update the non global setting
            this.buildSettings.put(buildNode, updatedSettings);
        }
    }
}
