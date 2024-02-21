//Colored print in pipeline console output...

def colored(String value, String color = ['green', 'red']) {
  switch (color) {
    case 'red':
      println('\033[1;31m' + "${value}" + '\033[0m')
      break
    case 'green':
      println('\033[1;32m' + "${value}" + '\033[0m')
      break
  }
}
