def renderSlackMessage(template, buildStatus, additionalInfo) {
    def TEXT = buildStatus == 'SUCCESS' ? ':white_check_mark: *Build Successful*' : ':x: *Build Failed*'

    def message = template
        .replace('$COLOR', (buildStatus == 'SUCCESS' ? 'good' : 'danger'))
        .replace('$TEXT', TEXT)
        .replace('$UPDATE_MSG', "Additional Information: $additionalInfo")

    return message
}
