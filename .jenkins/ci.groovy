@Library('add-ons-shared-libs@develop') _

node {
    continuousIntegrationPipeline(
        buildType: "deploy",
        sonar: [
            enable: false,
            projectKey: "eclipse-kura_kura-s7",
            tokenId: "sonarcloud-token-kura-s7",
            exclusions: "tests/**/*.java"
        ],
    )
}
