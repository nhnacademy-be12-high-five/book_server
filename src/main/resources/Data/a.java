/*
 * +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
 * + Copyright 2024. NHN Academy Corp. All rights reserved.
 * + * While every precaution has been taken in the preparation of this resource,  assumes no
 * + responsibility for errors or omissions, or for damages resulting from the use of the information
 * + contained herein
 * + No part of this resource may be reproduced, stored in a retrieval system, or transmitted, in any
 * + form or by any means, electronic, mechanical, photocopying, recording, or otherwise, without the
 * + prior written permission.
 * +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
 */

package com.nhnacademy.exam.parser.impl;

import com.nhnacademy.exam.dto.department.DepartmentMemberDto;
import com.nhnacademy.exam.parser.DepartmentParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class CsvDepartmentParser implements DepartmentParser {
    private static final String FILE_TYPE = ".csv";

    @Override
    public String getFileType() {
        return FILE_TYPE;
    }

    @Override
    public List parsing(File file) throws IOException {
        List<DepartmentMemberDto> members = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file)))) {
            String line;
            boolean isHeader = true;
            while ((line = reader.readLine()) != null) {
                // 첫번째 라인은 건너뛰기
                if (isHeader) {
                    isHeader = false;
                    continue;
                }
                // 공백 라인 건너 뛰기
                if (line.trim().isEmpty()) {
                    continue;
                }
                // , 단위로 쪼개기
                String[] data = line.split(",");

                //열의 개수가 부족한 경우
                if (data.length != 4) {
                    log.error("Invalid CSV line: {}", line);
                    continue;
                }

                // 컬럼마다 공백 제거
                String id = data[0].trim();
                String name = data[1].trim();
                String department = data[2].trim();
                String departmentCode = data[3].trim();

                members.add(new DepartmentMemberDto(id, name, department, departmentCode));
            }
        }
        return members;
    }
}