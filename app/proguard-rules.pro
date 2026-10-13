# Baromi ProGuard/R8 rules. R8 runs on release builds (isMinifyEnabled = true).
# None needed today: Room, AndroidX and Material bring their own consumer rules, classes named in the
# manifest and layouts are kept by AAPT2, and the app uses no reflection or enum-by-name lookups.
# Add a rule here only for a class R8 cannot see being used.
