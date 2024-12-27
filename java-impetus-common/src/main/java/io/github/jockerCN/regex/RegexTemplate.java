package io.github.jockerCN.regex;

import java.util.regex.Pattern;

/**
 * @author jokerCN <a href="https://github.com/jocker-cn">
 */
public abstract class RegexTemplate {


    public static final Pattern NUMBER_PATTERN = java.util.regex.Pattern.compile("([\\d.]+)\\s*([a-zA-Z]*)");

}
