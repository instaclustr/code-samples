INSERT INTO toys SELECT now() - floor(randUniform(0, 120)), floor(randUniform(0, 100)), number FROM numbers(300)

INSERT INTO boxes SELECT now() - floor(randUniform(0, 120)), floor(randUniform(0, 100)), number FROM numbers(300)
