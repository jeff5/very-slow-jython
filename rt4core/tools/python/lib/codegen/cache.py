# codegen.cache module

from collections.abc import Sequence, Iterable, Mapping
from pathlib import Path
from typing import Any, Callable

import re

# When we read and write the cache file, we swap a string key and
# some number of objects for a sequence of strings.
# The encoder/decoder pair has signatures:
EncoderType = Callable[[str, Any], Sequence[str]] | None
DecoderType = Callable[[Sequence[str]], tuple[str, Any]] | None

class CodeTranslationCache:
    """Class to load and save mappings named by client code.

    These are mappings we use in code translation. Each mapping may be
    initialised from a file of the same name and written to a file of
    that name at the end. Read and written files are potentially in
    different locations.
    """
    # Matchers used when reading the cache file
    COMMENT = re.compile(r"\s*(#.*)\n")
    KIND = re.compile(r"\w+")
    #HEAD = re.compile(r"(\w+)\n")

    def __init__(self, *names, cache_file_suffix=".cache"):
        # Maps name to value, encoder, decoder
        self.maps: dict[str, tuple[dict, EncoderType, DecoderType]] = {}
        self.suffix = cache_file_suffix
        for name in names:
            self.add_map(name)

    def add_map(self, name: str, *, encoder: EncoderType=None, decoder: DecoderType=None):
        """Add name as a dictionary attribute"""
        if not CodeTranslationCache.KIND.fullmatch(name) or hasattr(self, name):
            raise ValueError("the map name must be a new attribute name")
        mapping = {}
        self.maps[name] = (mapping, encoder, decoder)
        setattr(self, name, mapping)

    def save(self, cache_dir: Path):
        """Save contents of this cache to files at given path"""
        cache_dir.mkdir(parents=True, exist_ok=True)
        for kind, (map, encoder, _) in self.maps.items():
            path = (cache_dir/kind).with_suffix(self.suffix)
            with open(path, 'wt', encoding='utf-8', newline='\n') as f:
                for k, v in map.items():
                    if encoder is None:
                        # No fancy encoding, so the entry is just two lines
                        key, value = k, [str(v)]
                    else:
                        # There is an encoder:
                        lines = encoder(k, v)
                        key, value = lines[0], lines[1:]
                    # Write the (encoded) key and value lines.
                    f.write(key + '\n')
                    for line in value:
                        # Write the (encoded) value indented and escape newlines.
                        if ('\n' in line):
                            line = line.replace('\n', '\\\n')
                        f.write('  ' + line + '\n')

    def load(self, cache_dir: Path):
        """Load each kind in the cache from its own file"""

        def decode(parts:Sequence[str]) -> tuple[str, Any]:
            """Add lines of an entry to corresponding map"""
            # Apply the right decoder for the kind of entry
            if decoder:
                k, v = decoder(parts)
            elif len(parts) == 2:
                k, v = parts
            elif len(parts) == 1:
                k, v = parts[0], ['']
            else:
                # ignore empty entry
                k, v = None, None
            # Finally put the value in the map
            if k:
                map[k] = v

        def parse():
            """Parse input and add entries to its map"""
            lineno = 0
            line = ''

            def getline() -> None:
                nonlocal lineno, line
                line = input.read()
                lineno += 1
                print(repr(line))

            SPACE = (' ', '\t', '\n')

            while len(line) > 0:
                # line is un-processed input (and not empty)
                if CodeTranslationCache.COMMENT.match(line):
                    line = getline()
                elif not line.startswith(SPACE):
                    # Start an entry
                    parts, line = [line], getline()
                    while line.startswith(SPACE): # indented or empty
                        parts.append(line.strip().replace("\\n", '\n'))
                        line = getline()
                    decode(parts)
                else:
                    raise ValueError(f"Cache: unrecognised line {lineno}:\n{line:!r}")

        # Load each named map from its own-name file
        for kind, (map, _, decoder) in self.maps.items():
            path = (cache_dir/kind).with_suffix(self.suffix)
            if path.exists():
                with open(path, 'wt', encoding='utf-8') as input:
                    parse() # refs input, decoder


FunctionArgsType = dict[str, str] # items are (argname, argtype)

def function_encoder(c_name: str, parts: Sequence[Any]) -> Sequence[str]:
    """Encode function info as c_name, c_args, java_name, java_args"""

    def encode_args(args: dict[str, tuple[str]]):
        if not isinstance(args, Mapping):
            raise ValueError(f"function specification args must be a name:type map")
        decls = []
        for argtype, argname in args.items():
            decls.append(f"{argtype} {argname}")
        return '(' + ', '.join(decls) + ')'

    if len(parts) != 3 or not isinstance(parts[1], str):
        raise ValueError(f"function specification must be (name, args, name, args)")

    c_args = encode_args(parts[0])
    java_name = parts[1]
    java_args = encode_args(parts[2])
    
    return c_name, c_args, java_name, java_args

def function_decoder(lines:Sequence[str]) -> tuple[str, Sequence[Any]]:
    """Decode function from c_name, c_args, java_name, java_args"""

    def decode_args(line:str) -> Mapping[str, str]:
        args = {}
        for arg in line.split(','):
            argtype, argname = arg.split()
            args[argname] = argtype
        return args

    if len(lines) != 4:
        raise ValueError(f"function specification must be (name, args, name, args)")

    c_name, line2, java_name, line4 = lines
    c_args = decode_args(line2)
    java_args = decode_args(line4)

    return c_name, (c_args, java_name, java_args)
