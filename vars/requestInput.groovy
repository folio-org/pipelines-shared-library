def userInput = input(
    id: 'userInput', message: 'Enter key and value',
    parameters: [
        string(defaultValue: '',
            description: 'secret key',
            name: 'key'),
        string(defaultValue: '',
            description: 'secret value',
            name: 'value'),
        booleanParam(name: 'JSON',
            defaultValue: false,
            description: 'JSON type of secret')
    ])
