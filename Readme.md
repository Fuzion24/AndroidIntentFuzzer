Android Null Intent Fuzzer
=================

Grabbed from here : https://www.isecpartners.com/tools/mobile-security/intent-fuzzer.aspx with a few small additions made

Intent Fuzzer is a tool that can be used on any device using the Google Android operating system (OS). Intent Fuzzer is exactly what is seems, which is a fuzzer. It often finds bugs that cause the system to crash or performance issues on the device. The tool can either fuzz a single component or all components. It works well on Broadcast receivers, and average on Services. For Activities, only single Activities can be fuzzed, not all them. Instrumentations can also be started using this interface, and content providers are listed, but are not an Intent based IPC mechanism.

Pre-Requisites: Mobile Device Running Google's Android OS

