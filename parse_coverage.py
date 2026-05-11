import csv
import os

modules = ["ms-users", "ms-tournament", "ms-game", "ms-notifications"]

print("COVERAGE REPORT")
for mod in modules:
    csv_path = f"{mod}/target/site/jacoco/jacoco.csv"
    if not os.path.exists(csv_path):
        print(f"{mod}: ERROR - jacoco.csv not found")
        continue
    
    missed_lines = 0
    covered_lines = 0
    missed_branches = 0
    covered_branches = 0
    missed_instructions = 0
    covered_instructions = 0
    
    with open(csv_path, newline='') as csvfile:
        reader = csv.DictReader(csvfile)
        for row in reader:
            missed_lines += int(row["LINE_MISSED"])
            covered_lines += int(row["LINE_COVERED"])
            missed_branches += int(row["BRANCH_MISSED"])
            covered_branches += int(row["BRANCH_COVERED"])
            missed_instructions += int(row["INSTRUCTION_MISSED"])
            covered_instructions += int(row["INSTRUCTION_COVERED"])
            
    total_lines = missed_lines + covered_lines
    total_branches = missed_branches + covered_branches
    total_instructions = missed_instructions + covered_instructions
    
    line_cov = (covered_lines / total_lines * 100) if total_lines > 0 else 0
    branch_cov = (covered_branches / total_branches * 100) if total_branches > 0 else 0
    inst_cov = (covered_instructions / total_instructions * 100) if total_instructions > 0 else 0
    
    print(f"{mod}:")
    print(f"  Line Coverage: {line_cov:.2f}% ({covered_lines}/{total_lines})")
    print(f"  Branch Coverage: {branch_cov:.2f}% ({covered_branches}/{total_branches})")
    print(f"  Instruction Coverage: {inst_cov:.2f}% ({covered_instructions}/{total_instructions})")
    
print("\nTEST FAILURES (if any)")
for mod in modules:
    log_path = f"{mod}-coverage.log"
    with open(log_path, 'r') as logfile:
        lines = logfile.readlines()
        failures = [l for l in lines if "Failures: " in l or "Errors: " in l]
        if failures:
            for f in failures[-1:]: # get last summary
                print(f"{mod}: {f.strip()}")
