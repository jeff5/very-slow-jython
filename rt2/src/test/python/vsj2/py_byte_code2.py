# Generate examples for the bytecode interpreter

import os.path
import vsj2.py_byte_code1

if __name__ == "__main__":
    # Derive the examples file name from this one
    print("    // Code generated by {}".format(os.path.basename(__file__)))
    vsj2.py_byte_code1.main(".ex".join(os.path.splitext(__file__)))
