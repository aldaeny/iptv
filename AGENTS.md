# Repository architecture rules

- Keep real screen-projection regression tests in the phone app's androidTest source set and reuse the receiver's production crypto, protocol, and decoder sources only in that test source set; this verifies the complete capture-to-receiver path without packaging receiver code into phone releases.
- Run projection regressions in a separate debug-only Android emulator workflow with read-only repository permissions and no signing inputs; test success must not grant distribution or publication eligibility.
