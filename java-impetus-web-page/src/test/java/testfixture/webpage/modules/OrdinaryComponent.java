package testfixture.webpage.modules;

import org.springframework.stereotype.Component;

/** Native components still register alongside the metadata-only PageModule filter. */
@Component
public class OrdinaryComponent {
}
