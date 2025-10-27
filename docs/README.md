# AI Shopping Mall - Project Documentation

이 폴더는 GitHub Pages를 통해 프로젝트 소개 페이지로 배포됩니다.

## 페이지 구성

- `index.html`: 프로젝트 메인 소개 페이지
- `api.html`: REST API 문서

## GitHub Pages 배포 방법

### 1. GitHub에서 설정

1. 이 레포지토리의 Settings → Pages로 이동
2. Source 섹션에서:
   - Branch: `master` (또는 `main`) 선택
   - Folder: `/docs` 선택
3. Save 클릭

### 2. 배포 확인

5분 정도 후 다음 URL에서 확인 가능:
```
https://yourusername.github.io/shopingmall
```

## 로컬에서 확인

간단한 HTTP 서버로 로컬에서 미리보기:

```bash
# Python 3
python -m http.server 8000

# Node.js
npx http-server
```

브라우저에서 `http://localhost:8000` 접속

## 페이지 수정

HTML/CSS 파일을 직접 수정하고 Git으로 푸시하면 자동으로 업데이트됩니다.

```bash
git add docs/
git commit -m "Update project pages"
git push origin master
```

## 사용자 정보 변경

`index.html`과 `api.html`에서 다음 정보를 본인 것으로 변경하세요:
- GitHub username (`sungho` → 본인 username)
- 이메일 주소
- 프로젝트 설명
